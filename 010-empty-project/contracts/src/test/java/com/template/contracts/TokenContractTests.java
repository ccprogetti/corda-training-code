package com.template.contracts;

import static java.util.Arrays.asList;
import static net.corda.testing.node.NodeTestUtils.ledger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.template.states.TokenState;

import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;

public class TokenContractTests {

	static private final MockServices ledgerServices = new MockServices(
			asList("com.template.contracts", "com.template.flows"));
	static private final TestIdentity megaCorp = new TestIdentity(new CordaX500Name("MegaCorp", "London", "GB"));
	static private final TestIdentity miniCorp = new TestIdentity(new CordaX500Name("MiniCorp", "London", "GB"));
	static private final TestIdentity otherCorp = new TestIdentity(new CordaX500Name("OtherCorp", "London", "GB"));
	static private final Integer TokenValue = 1;

	@Test
	public void issueTransactionMustIncludeCommand() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.fails();
				tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()),
						new TokenContract.Commands.IssueAction());
				tx.verifies();
				return null;
			});
			return null;
		}));
	}

	@Test
	public void issueTransactionMustHaveNoInputs() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.input(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()),
						new TokenContract.Commands.IssueAction());
				tx.failsWith("No inputs should be consumed when issuing an Token.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void issueTransactionOutputNotEmpty() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()),
						new TokenContract.Commands.IssueAction());
				tx.failsWith("Only one output state should be created.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void issueTransactionOwnerMustBeDifferentToIssuerOutput() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), miniCorp.getParty()));
				tx.command(ImmutableList.of(miniCorp.getPublicKey()), new TokenContract.Commands.IssueAction());
				tx.failsWith("The lender and the borrower cannot be the same entity.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void moveTransactionOutQuantityMustBePositive() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.output(TokenContract.ID, new TokenState(-TokenValue, miniCorp.getParty(), otherCorp.getParty()));
				tx.input(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey()), new TokenContract.Commands.MoveAction());
				tx.failsWith("Output token quantity must be non negative.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void moveTransactionInQuantityMustBePositive() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), otherCorp.getParty()));
				tx.input(TokenContract.ID, new TokenState(-TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey()), new TokenContract.Commands.MoveAction());
				tx.failsWith("Input token quantity must be non negative.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void redeemTransactionMustHaveNoOutpouts() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				tx.input(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.output(TokenContract.ID, new TokenState(TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey(), miniCorp.getPublicKey()),
						new TokenContract.Commands.RedeemAction());
				tx.failsWith("No Outputs should be consumed when issuing an Token.");
				return null;
			});
			return null;
		}));
	}

	@Test
	public void redeemTransactionOutQuantityMustBePositive() {
		ledger(ledgerServices, (ledger -> {
			ledger.transaction(tx -> {
				//tx.output(TokenContract.ID, new TokenState(-TokenValue, miniCorp.getParty(), otherCorp.getParty()));
				tx.input(TokenContract.ID, new TokenState(-TokenValue, miniCorp.getParty(), megaCorp.getParty()));
				tx.command(ImmutableList.of(megaCorp.getPublicKey()), new TokenContract.Commands.RedeemAction());
				tx.failsWith("The Tokens's quantity must be non-negative.");
				return null;
			});
			return null;
		}));

	}

}