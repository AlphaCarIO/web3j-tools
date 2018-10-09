package com.alphacar.ethtools;

import com.alphacar.utils.AddressUtil;
import com.alphacar.utils.ProcessExcelTools;
import com.alphacar.utils.TransferInfo;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ChainId;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TokenTransfer {

    private ExecutorService newSingleThreadExecutor = Executors.newSingleThreadExecutor();

    private Lock lock = new ReentrantLock();

    private volatile boolean running = true;

    private class ExitHandler extends Thread {

        public ExitHandler() {
            super("Exit Handler");
        }

        public void run() {
            System.out.println("Set exit");
            running = false;
        }

    }

    private Web3j web3j;

    private String fromAddress = "";

    private String contractAddress = "";

    private Object tokenAbi = null;

    private static int decimals = 18;

    private byte chainId = ChainId.ROPSTEN;

    private int gasPrice = 10;

    private String baseUrl = "https://ropsten.etherscan.io/tx/";

    private String url = "https://ropsten.infura.io/4gmRz0RyQUqgK0Q1jdu5";

    private Credentials credentials = null;

    private static int sleepDuration = 5000;

    private ArrayList<TransferInfo> infos = null;

    private boolean needTransfer = false;

    private ExitHandler exitHandler;

    private String output_file;

    public TokenTransfer() {
        exitHandler = new ExitHandler();
        Runtime.getRuntime().addShutdownHook(exitHandler);
    }

    public static void main(String[] args) throws InterruptedException {

        String configFile = "";

        if (args.length >= 1) {
            configFile = args[0];
        }

        String transferListFile = "";

        if (args.length >= 2) {
            transferListFile = args[1];
        }

        boolean needTransfer = false;

        if (args.length >= 3) {
            if (args[2].toLowerCase().equals("t")) {
                needTransfer = true;
            }
        }

        System.out.println("configFile=" + configFile);
        System.out.println("transferListFile=" + transferListFile);

        ArrayList<TransferInfo> infos = null;

        try {
            infos = ProcessExcelTools.POIReadExcel(transferListFile);

        } catch (IOException e) {
            e.printStackTrace();
        }

        TokenTransfer tokenTransfer = new TokenTransfer();

        Yaml yaml = new Yaml();

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(configFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        /*
        InputStream inputStream = tokenTransfer.getClass()
                .getClassLoader()
                .getResourceAsStream("config.yaml");
        */

        Map<String, Object> obj = yaml.load(inputStream);

        String outputFileName = transferListFile.substring(transferListFile.lastIndexOf("/"),
                transferListFile.lastIndexOf(".")) + "_report.csv";
        tokenTransfer.init(obj, infos, needTransfer, outputFileName);

        tokenTransfer.process();

    }

    public void init(Map<String, Object> params, ArrayList<TransferInfo> infos, boolean needTransfer, String output_file) {

        this.needTransfer = needTransfer;

        this.output_file = output_file;

        this.infos = infos;

        Map<String, Object> web3_info = (Map<String, Object>) params.get("web3");

        url = (String) web3_info.get("url");
        web3j = Web3j.build(new HttpService(url));

        fromAddress = (String) web3_info.get("address");
        chainId = ((Integer)web3_info.get("chainId")).byteValue();

        if (chainId == '1') {
            baseUrl = "https://www.etherscan.io/tx/";
        }

        System.out.println("chainId:" + chainId);

        String privateKeyPath = (String) web3_info.get("privatekey_path");
        String password = (String) web3_info.get("password");

        try {
            File keyFile = null;

            boolean markDel = false;

            keyFile = new File(privateKeyPath);

            /*

            if (privateKeyPath.startsWith("/")) {
                keyFile = new File(privateKeyPath);
            } else {
                InputStream is = this.getClass().getClassLoader()
                        .getResourceAsStream("config.yaml");

                byte[] buffer = new byte[is.available()];
                is.read(buffer);

                keyFile = new File("config.tmp");
                OutputStream outStream = new FileOutputStream(keyFile);
                outStream.write(buffer);

                markDel = true;
            }

            */

            credentials = WalletUtils.loadCredentials(password, keyFile);

            if (markDel) {
                keyFile.delete();
            }

        } catch (Exception e) {
            System.err.println(e);
        }

        Map<String, Object> token_info = (Map<String, Object>) params.get("token");
        contractAddress = (String) token_info.get("token_address");
        tokenAbi = token_info.get("token_abi");
        System.out.println(contractAddress);
        System.out.println(tokenAbi);

        /*
        JSONArray jsonArr = JSONArray.fromObject(tokenAbi);
        for(int i=0;i<jsonArr.size();i++){
            JSONObject obj_temp = jsonArr.getJSONObject(i);
        }
        */

    }

    private final static String EOL = System.getProperty("line.separator");

    public void process() {

        double total_amt = 0;

        try {

            if (this.infos != null) {

                int counter = -1;

                for (TransferInfo info : infos) {

                    final String toAddress = AddressUtil.formatAddress(info.getEthAddress());

                    if (toAddress.equals("")) {
                        System.out.println(info + " got empty address");
                        continue;
                    }
                    total_amt += info.getAmount();

                    counter += 1;

                    info.setFormattedEthAddress(toAddress);

                    info.setId(counter);

                    //newSingleThreadExecutor.execute(() -> {

                    System.out.println("--------");

                    System.out.println((info.getId() + 1) + "/" + infos.size() + " " + info.toString());

                    if (TokenTransfer.this.needTransfer) {
                        sendToken(info.getId(), credentials, fromAddress, contractAddress, toAddress, info.getAmount(), decimals);
                    }

                    //});
                }

            }

        } catch (Exception e) {
            System.err.println(e);
        }

        //newSingleThreadExecutor.shutdown();

        System.out.println("total_amt:" + total_amt);

        if (web3j != null) {
            web3j.shutdown();
        }

        Runtime.getRuntime().removeShutdownHook(exitHandler);
        exitHandler = null;

        File output = new File("./" + output_file);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(output));
            writer.write("ethAddress,formattedEthAddress,amount,receipt" + EOL);

            for (TransferInfo info : infos) {
                writer.write(info.toString() + EOL);
            }

            writer.write(EOL + "total amount," + total_amt + EOL);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("-----run end!");

    }

    private Optional<TransactionReceipt> sendTransactionReceiptRequest(
            String transactionHash) throws Exception {
        EthGetTransactionReceipt transactionReceipt =
                web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get();

        return transactionReceipt.getTransactionReceipt();
    }

    private void sendToken(int id, Credentials credentials,
                           String fromAddress, String contractAddress, String toAddress, double amount, int decimals) {
        BigInteger nonce;
        EthGetTransactionCount ethGetTransactionCount = null;
        try {
            ethGetTransactionCount = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ethGetTransactionCount == null) return;
        nonce = ethGetTransactionCount.getTransactionCount();
        System.out.println("nonce " + nonce);
        BigInteger gasPrice = Convert.toWei(BigDecimal.valueOf(this.gasPrice), Convert.Unit.GWEI).toBigInteger();
        BigInteger gasLimit = BigInteger.valueOf(60000);
        BigInteger value = BigInteger.ZERO;
        //token转账参数
        String methodName = "transfer";
        List<Type> inputParameters = new ArrayList<>();
        List<TypeReference<?>> outputParameters = new ArrayList<>();
        Address tAddress = new Address(toAddress);
        Uint256 tokenValue = new Uint256(BigDecimal.valueOf(amount).multiply(BigDecimal.TEN.pow(decimals)).toBigInteger());
        inputParameters.add(tAddress);
        inputParameters.add(tokenValue);
        TypeReference<Bool> typeReference = new TypeReference<Bool>() {
        };
        outputParameters.add(typeReference);
        Function function = new Function(methodName, inputParameters, outputParameters);
        String data = FunctionEncoder.encode(function);

        String signedData;
        try {

            signedData = signTransaction(nonce, gasPrice, gasLimit, contractAddress, value, data, chainId, credentials);
            if (signedData != null) {
                EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedData).send();
                String txHash = ethSendTransaction.getTransactionHash();

                System.out.println("processing txHash:" + txHash);

                // poll for transaction response via org.web3j.protocol.Web3j.ethGetTransactionReceipt(<txHash>)

                Optional<TransactionReceipt> receiptOptional = sendTransactionReceiptRequest(txHash);

                while (running) {
                    if (!receiptOptional.isPresent()) {
                        Thread.currentThread().sleep(sleepDuration);
                        receiptOptional = sendTransactionReceiptRequest(txHash);
                    } else {
                        break;
                    }
                }

                lock.lock();
                if (infos != null) {
                    String tx_url = this.baseUrl + receiptOptional.get().getTransactionHash();
                    infos.get(id).setReceipt(tx_url);
                    System.out.println("tx_url:" + tx_url);
                }
                lock.unlock();

                //System.out.println("receiptOptional=" + receiptOptional);
            }
        } catch (Exception e) {
            e.printStackTrace();
            lock.unlock();
        }
    }

    /**
     * 签名交易
     */
    public static String signTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String
            to,
                                         BigInteger value, String data, byte chainId, Credentials credentials) throws IOException {
        byte[] signedMessage;
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                to,
                value,
                data);

        if (chainId > ChainId.NONE) {
            signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials);
        } else {
            signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        }

        String hexValue = Numeric.toHexString(signedMessage);
        return hexValue;
    }

}
