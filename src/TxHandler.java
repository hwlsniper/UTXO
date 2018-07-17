import java.util.ArrayList;
import java.util.Arrays;
//import java.util.stream.Stream;

public class TxHandler {

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public  UTXOPool utxoPool;

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {

        double totalInputValue = 0.0;
        double totalOutputValue = 0.0;
        UTXOPool dirtyUtxos = new UTXOPool();
        
        for (int i=0; i<tx.numInputs(); i++) {

            Transaction.Input in = tx.getInput(i);

            byte[] prevTrxHash = in.prevTxHash;
            int outIndex = in.outputIndex;

            UTXO tmpOut = new UTXO(prevTrxHash, outIndex);

            Transaction.Output sourceOut = this.utxoPool.getTxOutput(tmpOut);

            // if the claimed output not exists in the current UTXO => attempt to double spending.
            if (sourceOut == null) {
                return false;
            }

            // if this input source where already used in that transaction - reject it
            if ( dirtyUtxos.getTxOutput(tmpOut) != null) {
                return  false;
            }

            // add the output the the dirty transaction ds
            dirtyUtxos.addUTXO(tmpOut, sourceOut);

            // check if the input signature is valid
            if (!Crypto.verifySignature(sourceOut.address, tx.getRawDataToSign(i), in.signature)) {
                return false;
            }

            totalInputValue += sourceOut.value;
        }

        for (Transaction.Output targetOut : tx.getOutputs()) {

            // decline invalid (negative) output value;
            if (targetOut.value < 0) return false;

            totalOutputValue += targetOut.value;
        }

        System.out.println("totalInputValue: " + totalInputValue);
        System.out.println("totalOutputValue: " + totalOutputValue);

        // total input sum must be grater or equal to the total output sum
        boolean res = totalInputValue >= totalOutputValue;
        System.out.println("res: " + res);
        return res;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {

        // filter invalid transaction (according to the current UTXO state).
        ArrayList<Transaction> validTxs = this.getValidTransaction(possibleTxs);

        ArrayList<Transaction> approvedTxs = new ArrayList();

        while (validTxs != null && validTxs.size() > 0) {

            // remove the transaction from the possible valid transactions.
            Transaction tx = validTxs.remove(0  );

            // add the transaction the set of the approved transactions
             approvedTxs.add(tx);

            // remove all the claimed tx, from current utxoPool
            for (Transaction.Input in : tx.getInputs()) {
                this.utxoPool.removeUTXO(new UTXO(tx.getHash(), in.outputIndex ));
            }

            // update validTxs collection - as a result of utxoPool modifications.
            validTxs = this.getValidTransaction(validTxs);
        }

        return approvedTxs.toArray(new Transaction[approvedTxs.size()]);
    }

    private ArrayList<Transaction> getValidTransaction(ArrayList<Transaction> possibleTxs) {
        Transaction[] possibleTxsArray = possibleTxs.toArray(new Transaction[possibleTxs.size()]);
        Transaction[] validTxsArray  = Arrays.stream(possibleTxsArray).filter(x -> this.isValidTx(x)).toArray(size -> new Transaction[size]);
        ArrayList<Transaction> validTxs = new ArrayList(Arrays.asList(validTxsArray));
        return validTxs;
    }

    private ArrayList<Transaction> getValidTransaction(Transaction[] possibleTxs) {
        Transaction[] validTxsArray  = Arrays.stream(possibleTxs).filter(x -> this.isValidTx(x)).toArray(size -> new Transaction[size]);
        ArrayList<Transaction> validTxs = new ArrayList<Transaction>(Arrays.asList(validTxsArray));
        return validTxs;
    }

}
