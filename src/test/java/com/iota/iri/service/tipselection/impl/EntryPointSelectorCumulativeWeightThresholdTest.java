package com.iota.iri.service.tipselection.impl;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

import java.util.ArrayList;
import java.util.List;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.service.tipselection.Walker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class EntryPointSelectorCumulativeWeightThresholdTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static TipsViewModel tipsViewModel;
    private static Walker walker;
    private static WalkValidator walkValidator;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(),
                logFolder.getRoot().getAbsolutePath(), 1000));
        tangle.init();

        tipsViewModel = Mockito.mock(TipsViewModel.class);
    }

    @Test
    public void returnsGenesisInSingleTxTangle() throws Exception {
        TransactionViewModel transaction = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                Hash.NULL_HASH,
                Hash.NULL_HASH),
                getRandomTransactionHash());
        transaction.store(tangle);

        final int threshold = 50;
        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(transaction.getBundleHash());
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold, walker, walkValidator);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertEquals(Hash.NULL_HASH, entryPoint);
    }

    @Test
    public void returnsCorrectTxInChain() throws Exception {
        final int threshold = 5;
        final int chainLength = 30;
        final int expectedEntrypoint = chainLength - 7;
        
        List<TransactionViewModel> transactions = new ArrayList<TransactionViewModel>();

        transactions.add(new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash()));

        for (int i = 0; i < chainLength; i++) {
            Hash prevTxHash = transactions.get(transactions.size() - 1).getHash();
            transactions.add(new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(prevTxHash, prevTxHash), getRandomTransactionHash()));
        }

        for (TransactionViewModel transaction : transactions) {
            transaction.store(tangle);
        }

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(transactions.get(transactions.size() - 1).getHash());
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold, walker, walkValidator);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertNotEquals(Hash.NULL_HASH, entryPoint);
        Assert.assertEquals(transactions.get(expectedEntrypoint).getHash(), entryPoint);
    }

    @Test
    public void returnsCorrectTxInWheatStockShape() throws Exception {
        final int threshold = 15;
        final int stalkLevels = 15;
        final int txPerLevel = 5;
        final int expectedStalkLevel = stalkLevels - 4;
        
        List<TransactionViewModel> mainStalk = new ArrayList<TransactionViewModel>();

        mainStalk.add(new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash()));
        mainStalk.get(0).store(tangle);

        for (int i = 0; i < stalkLevels - 1; i++) {
            Hash prevTxHash = mainStalk.get(mainStalk.size() - 1).getHash();
            TransactionViewModel mainStalkTx = new TransactionViewModel(
                getRandomTransactionWithTrunkAndBranch(prevTxHash, prevTxHash), getRandomTransactionHash());

            mainStalk.add(mainStalkTx);
            mainStalkTx.store(tangle);
            
            for (int j = 0; j < txPerLevel; j++) {
                new TransactionViewModel(
                    getRandomTransactionWithTrunkAndBranch(mainStalkTx.getHash(), mainStalkTx.getHash()), getRandomTransactionHash())
                    .store(tangle);
            }
        }

        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(mainStalk.get(mainStalk.size() - 1).getHash());
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold, walker, walkValidator);
        Hash entryPoint = entryPointSelector.getEntryPoint();

        Assert.assertNotEquals(Hash.NULL_HASH, entryPoint);
        Assert.assertEquals(mainStalk.get(expectedStalkLevel).getHash(), entryPoint);
    }

    public void callWalkerWhenSolidTipIsNull() throws Exception {
        final int threshold = 50;
        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(null);
        
        EntryPointSelector entryPointSelector = new EntryPointSelectorCumulativeWeightThreshold(tangle, tipsViewModel, threshold, walker, walkValidator);

        entryPointSelector.getEntryPoint();

        Mockito.verify(walker, Mockito.times(1)).walk(Mockito.eq(Hash.NULL_HASH), Mockito.any(), Mockito.any());
    }
}