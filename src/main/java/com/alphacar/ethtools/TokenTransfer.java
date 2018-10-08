package com.alphacar.ethtools;

import com.alphacar.utils.AddressUtil;
import com.alphacar.utils.ReadExcelTools;
import com.alphacar.utils.TransferInfo;
import net.sf.json.JSONArray;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TokenTransfer implements Runnable {

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

    private String url = "https://ropsten.infura.io/4gmRz0RyQUqgK0Q1jdu5";

    private Credentials credentials = null;

    private static int sleepDuration = 10000;

    private ArrayList<TransferInfo> infos = null;

    private  boolean needTransfer = false;

    private ExitHandler exitHandler;

    public TokenTransfer() {
        exitHandler = new ExitHandler();
        Runtime.getRuntime().addShutdownHook(exitHandler);
    }

    public void init(Map<String, Object> params, ArrayList<TransferInfo> infos, boolean needTransfer) {

        this.needTransfer = needTransfer;

        this.infos = infos;

        Map<String, Object> web3_info = (Map<String, Object>)params.get("web3");

        url = (String)web3_info.get("url");
        web3j = Web3j.build(new HttpService(url));

        fromAddress = (String)web3_info.get("address");
        String privateKeyPath = (String)web3_info.get("privatekey_path");
        String password = (String)web3_info.get("password");

        try {

            credentials = WalletUtils.loadCredentials(password, privateKeyPath);

        } catch (Exception e) {
            System.err.println(e);
        }

        Map<String, Object> token_info = (Map<String, Object>)params.get("token");
        contractAddress = (String)token_info.get("token_address");
        tokenAbi = token_info.get("token_abi");
        System.out.println(contractAddress);
        System.out.println(tokenAbi);

        JSONArray jsonArr = JSONArray.fromObject(tokenAbi);

        /*
        for(int i=0;i<jsonArr.size();i++){
            JSONObject obj_temp = jsonArr.getJSONObject(i);
        }
        */

    }

    public void run() {

        try {

            if (this.infos != null) {

                double total_amt = 0;

                for (TransferInfo info : infos) {

                    String toAddress = AddressUtil.formatAddress(info.getEthAddress());

                    if (toAddress.equals("")) {
                        System.out.println(info + " got empty address");
                        continue;
                    }
                    total_amt += info.getAmount();

                    System.out.println(info);
                    if (this.needTransfer) {
                        sendToken(credentials, fromAddress, contractAddress, toAddress, info.getAmount(), decimals);
                    }
                }

                System.out.println("total_amt:" + total_amt);

            }

        } catch (Exception e) {
            System.err.println(e);
        }

        if (web3j != null) {
            web3j.shutdown();
        }

        Runtime.getRuntime().removeShutdownHook(exitHandler);
        exitHandler = null;

        System.out.println("-----run end!");

    }

    public static void main(String[] args) throws InterruptedException {

        String fileName = "/Users/leo/Documents/src_codes/alphaauto/acar_ico/web3j-tools/src/main/resources/transfer20180929.xlsx";

        if (args.length >= 1) {
            fileName = args[0];
        }

        boolean needTransfer = false;

        if (args.length >= 2) {
            if (args[1].toLowerCase().equals("t")) {
                needTransfer = true;
            }
        }

        System.out.println("fileName=" + fileName);

        ArrayList<TransferInfo> infos = null;

        try {
            infos = ReadExcelTools.POIReadExcel(fileName);

        } catch (IOException e) {
            e.printStackTrace();
        }

        TokenTransfer ctrlc = new TokenTransfer();

        Yaml yaml = new Yaml();
        InputStream inputStream = ctrlc.getClass()
                .getClassLoader()
                .getResourceAsStream("config.yaml");
        Map<String, Object> obj = yaml.load(inputStream);

        ctrlc.init(obj, infos,  needTransfer);

        Thread t = new Thread(ctrlc);
        t.setName("ctrl c handler");
        t.start();
        t.join();

        System.out.println("-----after join!!!");

    }

    private Optional<TransactionReceipt> sendTransactionReceiptRequest(
            String transactionHash) throws Exception {
        EthGetTransactionReceipt transactionReceipt =
                web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get();

        return transactionReceipt.getTransactionReceipt();
    }

    private void sendToken(Credentials credentials,
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
        BigInteger gasPrice = Convert.toWei(BigDecimal.valueOf(3), Convert.Unit.GWEI).toBigInteger();
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
                System.out.println(txHash);
                // poll for transaction response via org.web3j.protocol.Web3j.ethGetTransactionReceipt(<txHash>)

                Optional<TransactionReceipt> receiptOptional =
                        sendTransactionReceiptRequest(txHash);

                while (running) {
                    if (!receiptOptional.isPresent()) {
                        Thread.currentThread().sleep(sleepDuration);
                        receiptOptional = sendTransactionReceiptRequest(txHash);
                    } else {
                        break;
                    }
                }

                System.out.println("receiptOptional=" + receiptOptional);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 签名交易
     */
    public static String signTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String to,
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
