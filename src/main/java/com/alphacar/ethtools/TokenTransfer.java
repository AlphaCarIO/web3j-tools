package com.alphacar.ethtools;

import com.alphacar.utils.AddressUtil;
import com.alphacar.utils.IOUtils;
import com.alphacar.utils.TransferInfo;
import com.alphacar.utils.Web3jHelper;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ChainId;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public class TokenTransfer {

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

    private String fromAddress = "";

    private String contractAddress = "";

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

    private Web3jHelper w3jHelper;

    public TokenTransfer() {
        exitHandler = new ExitHandler();
        Runtime.getRuntime().addShutdownHook(exitHandler);
    }

    public static void main(String[] args) {

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
            infos = IOUtils.POIReadExcel(transferListFile);

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

        Map<String, Object> obj = yaml.load(inputStream);

        String outputFileName = transferListFile.substring(transferListFile.lastIndexOf("/") + 1,
                transferListFile.lastIndexOf(".")) + "_report.csv";
        tokenTransfer.init(obj, infos, needTransfer, outputFileName);

        tokenTransfer.process();

    }

    public void init(Map<String, Object> params, ArrayList<TransferInfo> infos, boolean needTransfer, String output_file) {

        this.needTransfer = needTransfer;

        this.infos = infos;

        Map<String, Object> web3_info = (Map<String, Object>) params.get("web3");

        url = (String) web3_info.get("url");

        w3jHelper = new Web3jHelper(url);

        gasPrice = (Integer) web3_info.get("gasPrice");
        fromAddress = (String) web3_info.get("address");
        chainId = ((Integer) web3_info.get("chainId")).byteValue();

        if (chainId == ChainId.MAINNET) {
            baseUrl = "https://www.etherscan.io/tx/";
        }

        this.output_file = chainId + "_" + output_file;

        System.out.println("baseUrl:" + baseUrl);
        System.out.println("gasPrice:" + gasPrice);
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
        System.out.println(contractAddress);

    }

    public void process() {

        double total_amt = 0;

        try {

            if (this.infos != null) {

                int counter = -1;

                BigInteger nonce = w3jHelper.getNonce(fromAddress);

                for (TransferInfo info : infos) {

                    final String toAddress = AddressUtil.formatAddress(info.getEthAddress());

                    if (toAddress.equals("")) {
                        System.out.println(info + " got empty address");
                        continue;
                    }
                    total_amt += info.getAmount();

                    counter += 1;

                    info.setBaseUrl(this.baseUrl);

                    info.setFormattedEthAddress(toAddress);

                    if (info.getEthAddress().equals(info.getFormattedEthAddress())) {
                        info.setFlag("");
                    } else {
                        info.setFlag("***");
                    }

                    info.setId(counter);

                    System.out.println("--------");

                    if (TokenTransfer.this.needTransfer) {

                        String txHash = w3jHelper.sendToken(info.getId(), credentials, nonce, chainId,
                                gasPrice, contractAddress, toAddress, info.getAmount(), decimals);

                        info.setTxHash(txHash);

                        nonce = nonce.add(BigInteger.ONE);

                    }

                    System.out.println((info.getId() + 1) + "/" + infos.size() + " " + info.toString());

                }

                updateStatus();

            }

        } catch (Exception e) {
            System.err.println(e);
        }

        System.out.println("total_amt:" + total_amt);

        if (w3jHelper != null) {
            w3jHelper.release();
        }

        IOUtils.WriteResult(output_file, total_amt, infos);

        System.out.println("-----run end!");

    }

    private void updateStatus() throws Exception {

        System.out.println("start updateStatus!");

        if (infos != null) {

            boolean allConfirmed = false;

            while (running && !allConfirmed) {

                Thread.currentThread().sleep(sleepDuration);

                allConfirmed = true;

                for (TransferInfo info : infos) {
                    if (info.getTxHash() != null && !info.getTxHash().equals("")) {
                        if (info.getStatus().equals("")) {
                            Optional<TransactionReceipt> receiptOptional = w3jHelper.sendTransactionReceiptRequest(info.getTxHash());
                            if (receiptOptional.isPresent()) {
                                info.setStatus(receiptOptional.get().getStatus());
                            } else {
                                allConfirmed = false;
                            }
                        }
                    }
                }

                if (allConfirmed) {
                    break;
                }
            }
        }
    }

}
