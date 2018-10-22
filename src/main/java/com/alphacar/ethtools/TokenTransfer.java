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

class ErrorInfo {

    private String addr;

    private int num;

    private double amt;

    @Override
    public String toString() {
        return "(num:" + num + " addr:" + addr + " amt:" + amt + ")";
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public double getAmt() {
        return amt;
    }

    public void setAmt(double amt) {
        this.amt = amt;
    }
}

public class TokenTransfer {

    private volatile boolean running = true;

    private class ExitHandler extends Thread {

        ExitHandler() {
            super("Exit Handler");
        }

        @Override
        public void run() {
            System.out.println("Set exit");
            running = false;
        }

    }

    private String fromAddress = "";

    private String contractAddress = "";

    private int decimals = 18;

    private byte chainId = ChainId.ROPSTEN;

    private int gasPrice = 10;

    private int gasPerTx = -1;

    private String baseUrl = "https://ropsten.etherscan.io/tx/";

    private String url = "https://ropsten.infura.io/4gmRz0RyQUqgK0Q1jdu5";

    private Credentials credentials = null;

    private int sleepDuration = 3000;

    private ArrayList<TransferInfo> infos = null;

    private boolean needTransfer = false;

    private String output_file;

    private Web3jHelper w3jHelper;

    private ArrayList<ErrorInfo> errorInfos = new ArrayList<>();

    public TokenTransfer() {
        ExitHandler exitHandler = new ExitHandler();
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
                transferListFile.lastIndexOf("."));
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
        gasPerTx = (Integer) web3_info.get("gasPerTx");
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

            credentials = WalletUtils.loadCredentials(password, keyFile);

        } catch (Exception e) {
            System.err.println(e);
        }

        Map<String, Object> tokenInfo = (Map<String, Object>) params.get("token");
        contractAddress = (String) tokenInfo.get("token_address");
        System.out.println(contractAddress);

    }

    public void process() {

        double total_amt = 0;

        double errAmt = 0;

        try {

            if (this.infos != null) {

                long startTime = System.currentTimeMillis();

                int counter = -1;

                for (TransferInfo info : infos) {

                    final String toAddress = AddressUtil.formatAddress(info.getEthAddress());

                    if ("".equals(toAddress)) {
                        System.out.println(info + " got empty address");
                        ErrorInfo error = new ErrorInfo();
                        error.setAddr(info.getEthAddress());
                        error.setNum(counter);
                        error.setAmt(info.getAmount());
                        errorInfos.add(error);
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
                }

                if (errorInfos.size() > 0) {
                    System.out.println("transfer list has errors!");

                    for (ErrorInfo errInfo: errorInfos) {
                        System.out.println(errInfo);
                        errAmt += errInfo.getAmt();
                    }

                    System.exit(-1);
                }

                BigInteger totalBalance = w3jHelper.getTokenBalance(contractAddress, this.fromAddress);

                int offset = 4;

                double tb = totalBalance.divide(BigInteger.valueOf(10).pow(decimals - offset)).doubleValue() / (Math.pow(10, offset));

                System.out.println("fromAddress:" + fromAddress + " totalBalance:" + String.format("%.04f", tb));
                System.out.println(String.format("%.04f", total_amt) + " to be sent!");

                if (tb < total_amt) {
                    System.out.println("buffer account don't have enough token!");
                    System.exit(-1);
                }

                BigInteger nonce = w3jHelper.getNonce(fromAddress);

                for (TransferInfo info : infos) {

                    if (TokenTransfer.this.needTransfer) {

                        if (!"".equals(info.getFormattedEthAddress())) {

                            String txHash = w3jHelper.sendToken(info.getId(), credentials, nonce, chainId,
                                    gasPrice, contractAddress, info.getFormattedEthAddress(),
                                    info.getAmount(), decimals);

                            info.setTxHash(txHash);

                            System.out.println("just sent " + info.getAmount()
                                    + " token to " + info.getFormattedEthAddress()
                                    + " (" + (info.getId() + 1) + "/" + infos.size() + ").");

                        }

                        nonce = nonce.add(BigInteger.ONE);

                    }

                }

                System.out.println("total_amt:" + total_amt);

                IOUtils.WriteResult(output_file + "_txs.csv",
                        total_amt, 0, 0, infos);

                System.out.println("all_amt:" + (total_amt + errAmt) + "   error amt:" + errAmt);
                System.out.println("total count:" + infos.size() + " txs.");
                System.out.println("gas per tx:" + gasPrice * gasPerTx * 1e-9 + " ether");
                System.out.println("total gas:" + infos.size() * gasPrice * gasPerTx * 1e-9 + " ether.");

                long endTime = System.currentTimeMillis();

                long sendTime =  (endTime - startTime);

                System.out.println("sendToken time:" + sendTime + "ms");

                startTime = endTime;

                updateStatus();

                endTime = System.currentTimeMillis();

                long updateTime =  (endTime - startTime);

                System.out.println("updateStatus time:" + updateTime+ "ms");

                IOUtils.WriteResult(output_file + "_report.csv"
                        , total_amt, sendTime, updateTime, infos);

            }

        } catch (Exception e) {
            System.err.println(e);
        }

        if (w3jHelper != null) {
            w3jHelper.release();
        }

        System.out.println("-----run end!");

    }

    private void updateStatus() {

        System.out.println("start updateStatus!");

        if (infos != null) {

            boolean allConfirmed = false;

            while (running && !allConfirmed) {

                try {
                    Thread.currentThread().sleep(sleepDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                allConfirmed = true;

                int remaining_count = 0;

                for (TransferInfo info : infos) {
                    if (info.getTxHash() != null && !info.getTxHash().equals("")) {
                        if (info.getStatus().equals("")) {
                            Optional<TransactionReceipt> receiptOptional = null;
                            try {
                                receiptOptional = w3jHelper.sendTransactionReceiptRequest(info.getTxHash());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (receiptOptional != null && receiptOptional.isPresent()) {
                                info.setStatus(receiptOptional.get().getStatus());
                            } else {
                                allConfirmed = false;
                                remaining_count++;
                            }
                        }
                    }
                }

                if (allConfirmed) {
                    break;
                }
                System.out.println("remaining unconfirmed tx num:" + remaining_count);
            }
        }
    }

}
