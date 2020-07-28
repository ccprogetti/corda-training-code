package com.template.flows;

import java.util.Collections;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.states.AirMileTokenType;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Amount;
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
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to
 * an agreement about the Token encapsulated within an [TokenState].
 *
 * In our simple example, the [Acceptor] always accepts a valid Token.
 *
 * These flows have deliberately been implemented by using only the call()
 * method for ease of understanding. In practice we would recommend splitting up
 * the varTokens stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with
 * the @Suspendable annotation.
 */
public class IssueFlow {
	@InitiatingFlow
	@StartableByRPC
	public static class Initiator extends FlowLogic<SignedTransaction> {

		private final int quantity;
		private final Party holder;

		// issuer non serve perchè è colui che inizializza

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

		// The progress tracker checkpoints each stage of the flow and outputs the
		// specified messages when each
		// checkpoint is reached in the code. See the 'progressTracker.currentStep'
		// expressions within the call()
		// function.
		private final ProgressTracker progressTracker = new ProgressTracker(GENERATING_TRANSACTION,
				VERIFYING_TRANSACTION, SIGNING_TRANSACTION, GATHERING_SIGS, FINALISING_TRANSACTION);

		public Initiator(int quantity, Party otherParty) {

			if (quantity < 0)
				throw new NullPointerException("quantity must not be null");

			this.quantity = quantity;
			this.holder = otherParty;
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

			// TokenState outputTokenState = new TokenState(quantity, issuer, holder);

			// AirMileTokenType

			///// START
			// Prepare what we are talking about.
			final TokenType airTokenType = AirMileTokenType.create();
			final IssuedTokenType issuedAirMile = new IssuedTokenType(getOurIdentity(), airTokenType);
			final SecureHash contractAttachment = TransactionUtilitiesKt.getAttachmentIdForGenericParam(airTokenType);

			// Who is going to own the output, and how much?
			// Create a 100$ token that can be split and merged.
			final Amount<IssuedTokenType> oneHundredUSD = AmountUtilitiesKt.amount(quantity, issuedAirMile);

			final FungibleToken usdToken = new FungibleToken(oneHundredUSD, holder, contractAttachment);

			// Issue the token to Alice.
			return subFlow(new IssueTokens(Collections.singletonList(usdToken), // Output instances
					Collections.emptyList())); // Observers

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
