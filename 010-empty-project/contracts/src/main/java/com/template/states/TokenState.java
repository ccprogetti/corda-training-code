package com.template.states;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.template.contracts.TokenContract;

import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

// *********
// * State *
// *********
@BelongsToContract(TokenContract.class)
public class TokenState implements ContractState {

	private final Integer quantity;
	private final Party issuer;
	private final Party holder;
	

	
	public TokenState(Integer quantity, Party issuer, Party holder) {
		
		this.quantity = quantity;
		this.issuer = issuer;
		this.holder = holder;
	}


	@NotNull
	@Override
	public List<AbstractParty> getParticipants() {
		return Collections.singletonList(holder);
	}


	public Integer getQuantity() {
		return quantity;
	}


	public Party getIssuer() {
		return issuer;
	}


	public Party getHolder() {
		return holder;
	}

	

}