package com.template;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.template.flows.IssueFlow;
import com.template.states.TokenState;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.Vault.Page;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;

public class IssueFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode holder;
    private final StartedMockNode issuer;
   
	public IssueFlowsTests() throws Exception {
    	
    	 network = new MockNetwork(new MockNetworkParameters().withCordappsForAllNodes(ImmutableList.of(
                 TestCordapp.findCordapp("com.template.contracts"),
                 TestCordapp.findCordapp("com.template.flows"))));
    	 holder = network.createPartyNode(null);
         issuer = network.createPartyNode(null);
         // For real nodes this happens automatically, but we have to manually register the flow for tests.       
                 
        Arrays.asList(holder, issuer).forEach(it ->
                it.registerInitiatedFlow(IssueFlow.Acceptor.class));
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }
    
    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheIssuer() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(1, holder.getInfo().getLegalIdentities().get(0));
        final CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        tx.verifyRequiredSignatures();
    }
    
    
    

    @Test(expected = NullPointerException.class)
    public void quantityCannotBeNegative() throws InterruptedException, ExecutionException, FlowException {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(-1, holder.getInfo().getLegalIdentities().get(0));
        final CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();
        future.get();     
        
    }
    
    
    @Test
    public void lowRecordsTheCorrectTokenStateInHolderVault() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(1, holder.getInfo().getLegalIdentities().get(0));
        final CordaFuture<SignedTransaction> future = issuer.startFlow(flow);
        network.runNetwork();

        future.get();
        
        // We check the recorded IOU in both vaults.
        for (StartedMockNode node : ImmutableList.of(holder)) {
            node.transaction(() -> {            	
            	
            	QueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);           
                Page<TokenState> results = node.getServices().getVaultService().queryBy(TokenState.class, criteria);
                List<StateAndRef<TokenState>> tokenStates = results.getStates();
                //List<StateAndRef<TokenState>> tokens = tokenStates.stream().collect(Collectors.toList());                
                assertEquals(1, tokenStates.size());
                assertEquals(1, tokenStates.get(0).getState().getData().getQuantity());
                return null;
            });
        }       
        
        
        
        
    }

    
    
}