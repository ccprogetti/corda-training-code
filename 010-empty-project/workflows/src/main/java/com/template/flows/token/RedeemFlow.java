package com.template.flows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.template.contracts.TokenContract;
import com.template.states.TokenState;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.Vault.Page;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the Token encapsulated
 * within an [TokenState].
 *
 * In our simple example, the [Acceptor] always accepts a valid Token.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the varTokens stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class RedeemFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final int quantity;
        private final Party holder;
                
        //issuer non serve perchè è colui che inizializza

        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new Token.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(int quantity, Party holder, Party otherHolder) {
			this.quantity = quantity;
            this.holder = holder;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party issuer = getOurIdentity();
            
            //initilaize inputTokenState
            QueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);           
            Page<TokenState> results = getServiceHub().getVaultService().queryBy(TokenState.class, criteria);
            List<StateAndRef<TokenState>> tokenStates = results.getStates();
            List<StateAndRef<TokenState>> tokens = tokenStates.stream().filter(token -> token.getState().getData().getHolder().equals(holder)).collect(Collectors.toList());
            
            int tmpQuantity = 0;
            List<StateAndRef<TokenState>> inputTokens = new ArrayList<>();
            for (StateAndRef<TokenState> token: tokens) {
            	tmpQuantity = token.getState().getData().getQuantity();
            	inputTokens.add(token);
            	if (tmpQuantity >= quantity) {
            		break;
            	}
            }
            
                        
            List<TokenState> outputTokens = new ArrayList<>();
//            TokenState outputTokenState = new TokenState(quantity, issuer, otherHolder);
//            outputTokens.add(outputTokenState);
            
            
            //verify the difference
            if (tmpQuantity > quantity) {
            	outputTokens.add(new TokenState(tmpQuantity - quantity, issuer, holder));
            }
            
            final Set<Party> allSigners = inputTokens.stream()
                    // Only the input holder is necessary on a Move.
            		.map(it -> it.getState().getData().getHolder())
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .collect(Collectors.toSet());
            
                       
            // The issuer is a required signer, so we express this here
            final Command<TokenContract.Commands.RedeemAction> txCommand = new Command<>(
                    new TokenContract.Commands.RedeemAction(),
                    allSigners.stream().map(Party::getOwningKey).collect(Collectors.toList()));
            
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            
            
            inputTokens.forEach(txBuilder::addInputState);
            outputTokens.forEach(it -> txBuilder.addOutputState(it, TokenContract.ID));
            

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            FlowSession otherPartySession = initiateFlow(holder);
            
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx, ImmutableSet.of(otherPartySession)));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartySession;

        public Acceptor(FlowSession otherPartySession) {
            this.otherPartySession = otherPartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
//                    requireThat(require -> {
//                        ContractState output = stx.getTx().getOutputs().get(0).getData();
//                        require.using("This must be an Token transaction.", output instanceof TokenState);
//                        TokenState Token = (TokenState) output;
//                        require.using("I won't accept Tokens with a value over 100.", Token.getQuantity() <= 100);
//                        return null;
//                    });
                }
            }
            final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker());
            final SecureHash txId = subFlow(signTxFlow).getId();

            return subFlow(new ReceiveFinalityFlow(otherPartySession, txId));
        }
    }
}
