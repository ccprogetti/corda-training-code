package com.template.contracts;

import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.template.states.TokenState;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;

// ************
// * Contract *
// ************
public class TokenContract implements Contract {
	// This is used to identify our contract when building a transaction.
	public static final String ID = "com.template.contracts.TokenContract";

	
	@Override
	public void verify(@NotNull LedgerTransaction tx) {
		Command command = tx.getCommand(0);

		final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
		final List<TokenState> outputs = tx.outputsOfType(TokenState.class);

		final boolean hasAllPositiveQuantities = inputs.stream().allMatch(it -> 0 < it.getQuantity())
				&& outputs.stream().allMatch(it -> 0 < it.getQuantity());

		final Set<PublicKey> allInputHolderKeys = inputs.stream().map(it -> it.getHolder().getOwningKey())
				.collect(Collectors.toSet());

		if (command.getValue() instanceof Commands.IssueAction)
			verifyIssueTrx(tx, command, hasAllPositiveQuantities);

		else if (command.getValue() instanceof Commands.MoveAction)
			verifyMoveTrx(tx, command);

		else if (command.getValue() instanceof Commands.RedeemAction)
			verifyRedeemTrx(tx, command);

		else
			throw new IllegalArgumentException("Invalid Command");

	}

	public void verifyIssueTrx(@NotNull LedgerTransaction tx, Command<?> command, boolean hasAllPositiveQuantities) {

		requireThat(require -> {
			// Generic constraints around the Token transaction.
			require.using("No inputs should be consumed when issuing an Token.", tx.getInputs().isEmpty());

			require.using("Only one output state should be created.", tx.getOutputs().size() == 1);

			final TokenState out = tx.outputsOfType(TokenState.class).get(0);
			List<TokenState> outputs = tx.outputsOfType(TokenState.class);

			// FIXME: instead verify that the list of issuers should be conserved 
			require.using("The lender and the borrower cannot be the same entity.", out.getIssuer() != out.getHolder());

			// Constraints on the signers.
			require.using("The issuers should sign.", command.getSigners().containsAll(
					outputs.stream().map(it -> it.getIssuer().getOwningKey()).collect(Collectors.toSet())));

			// Token-specific constraints.
			require.using("The Tokens's quantity must be non-negative.", hasAllPositiveQuantities);

			return null;
		});

	}

	public void verifyMoveTrx(@NotNull LedgerTransaction tx, Command<?> command) {
		final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
		final List<TokenState> outputs = tx.outputsOfType(TokenState.class);

		final Set<PublicKey> allInputHolderKeys = inputs.stream().map(it -> it.getHolder().getOwningKey())
				.collect(Collectors.toSet());

		int inQuantity = inputs.stream().mapToInt(token -> token.getQuantity()).sum();
		int outQuantity = outputs.stream().mapToInt(token -> token.getQuantity()).sum();

		requireThat(require -> {

			require.using("Only one output state should be created.", !outputs.isEmpty());

			require.using("Only one input state should be created.", !inputs.isEmpty());

			final TokenState out = tx.outputsOfType(TokenState.class).get(0);
			final TokenState in = tx.inputsOfType(TokenState.class).get(0);

			final List<TokenState> outList = tx.outputsOfType(TokenState.class);

			require.using("The issure and the owner cannot be the same entity.", out.getIssuer() != out.getHolder());
			require.using("The owner in cannot be the same to older out.", out.getHolder() != in.getHolder());

			//TODO: The list of issuers should be conserved.
			//TODO: The sum of quantities for each issuer should be conserved

			// Constraints on the signers.
			require.using("The current holders should sign.", command.getSigners().containsAll(allInputHolderKeys));

			// Token-specific constraints.
			require.using("Output token quantity must be non negative.",
					outputs.stream().allMatch(it -> 0 < it.getQuantity()));
			require.using("Input token quantity must be non negative.",
					inputs.stream().allMatch(it -> 0 < it.getQuantity()));

			require.using("The Tokens's quantity in/out is the same.", inQuantity == outQuantity);

			return null;
		});

	}

	public void verifyRedeemTrx(@NotNull LedgerTransaction tx, Command<?> command) {

		final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
		final List<TokenState> outputs = tx.outputsOfType(TokenState.class);
		final Set<PublicKey> allInputHolderKeys = inputs.stream().map(it -> it.getHolder().getOwningKey())
				.collect(Collectors.toSet());

		final TokenState in = tx.inputsOfType(TokenState.class).get(0);
		final List<TokenState> outList = tx.inputsOfType(TokenState.class);

		requireThat(require -> {
			// Generic constraints around the Token transaction.
			require.using("No Outputs should be consumed when issuing an Token.", outputs.isEmpty());

			require.using("Only one input state should be created.", !inputs.isEmpty());

			require.using("The lender and the borrower cannot be the same entity.", in.getIssuer() != in.getHolder());

			// Constraints on the signers.
			require.using("The issuers should sign.", command.getSigners().containsAll(
					inputs.stream().map(it -> it.getIssuer().getOwningKey()).collect(Collectors.toSet())));

			// Constraints on the signers.
			require.using("The current holders should sign.", command.getSigners().containsAll(allInputHolderKeys));

			final boolean noneZero = outList.stream().noneMatch(token -> token.getQuantity() <= 0);

			require.using("The Tokens's quantity must be non-negative.", noneZero);

			return null;
		});

	}

	// Used to indicate the transaction's intent.
	public interface Commands extends CommandData {
		class IssueAction implements Commands {
		}

		class MoveAction implements Commands {
		}

		class RedeemAction implements Commands {
		}
	}
}