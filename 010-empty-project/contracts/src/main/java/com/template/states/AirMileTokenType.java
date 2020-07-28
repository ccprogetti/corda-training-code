package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;

public class AirMileTokenType extends TokenType {

		
	public static final String IDENTIFIER = "AIR-MILE";
	public static final int FRACTION_DIGITS = 0;
	
	private AirMileTokenType() {
		super(IDENTIFIER, FRACTION_DIGITS);
	}
	
	public static TokenType create() {
        return new TokenType(IDENTIFIER, FRACTION_DIGITS);
    }
	
}
