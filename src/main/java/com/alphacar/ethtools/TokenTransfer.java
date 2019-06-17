package com.alphacar.ethtools;

import com.alphacar.utils.*;
import org.apache.commons.cli.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ChainId;
import org.web3j.utils.Convert;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * @author leo
 */
public class TokenTransfer {

    private volatile boolean running = true;

    private static final String CONFIRM = "yes";

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

    private byte chainId = ChainId.ROPSTEN;

    private int gasPrice = 10;

    private int gasPerTx = -1;

    private String baseUrl = "https://ropsten.etherscan.io/tx/";

    private String url = "https://ropsten.infura.io/4gmRz0RyQUqgK0Q1jdu5";

    private Credentials credentials = null;

    private int sleepDuration = 3000;

    private List<TransferInfo> infos = null;

    private Map<String, String> extraInfos = new HashMap<>();

    private boolean needTransfer = false;

    private boolean needUpdate = true;

    private String outputFile = null;

    private Web3jHelper w3jHelper;

    private List<ErrorInfo> errorInfos = new ArrayList<>();

    public TokenTransfer() {
        ExitHandler exitHandler = new ExitHandler();
        Runtime.getRuntime().addShutdownHook(exitHandler);
    }

    public static void main(String[] args) {

        Options options = new Options();

        Option config = new Option("c", "config", true, "config file");
        config.setRequired(true);
        options.addOption(config);

        Option file = new Option("f", "transferFile", true, "transfer file");
        file.setRequired(true);
        options.addOption(file);

        Option gasPrice = new Option("g", "gasPrice", true, "gas price");
        gasPrice.setRequired(false);
        options.addOption(gasPrice);

        Option transfer = new Option("t", "transfer", false, "transfer flag");
        transfer.setRequired(false);
        options.addOption(transfer);

        Option update = new Option("u", "update", false, "update flag");
        update.setRequired(false);
        options.addOption(update);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
        }

        String configFilePath = cmd.getOptionValue("config");
        String transferFilePath = cmd.getOptionValue("transferFile");

        int gasPriceVal;
        try{
            System.out.println("cmd.getOptionValue(\"gasPrice\")" + cmd.getOptionValue("gasPrice"));
            gasPriceVal = Integer.parseInt(cmd.getOptionValue("gasPrice"));
        }   catch(Exception e) {
            e.printStackTrace();
            gasPriceVal = 0;
        }

        boolean needTransfer = cmd.hasOption("t");

        boolean needUpdate = cmd.hasOption("u");

        System.out.println(configFilePath);
        System.out.println(transferFilePath);

        System.out.println("needTransfer:" + needTransfer);
        System.out.println("needUpdate:" + needUpdate);

        System.out.println("gasPriceVal=" + gasPriceVal);

        System.out.println("configFilePath=" + configFilePath);
        System.out.println("transferFilePath=" + transferFilePath);

        ArrayList<TransferInfo> infos = null;

        try {
            infos = IOUtils.POIReadExcel(transferFilePath);

        } catch (IOException e) {
            e.printStackTrace();
        }

        TokenTransfer tokenTransfer = new TokenTransfer();

        Yaml yaml = new Yaml();

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(configFilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Map<String, Object> obj = yaml.load(inputStream);

        String outputFileName = transferFilePath.substring(transferFilePath.lastIndexOf("/") + 1,
                transferFilePath.lastIndexOf("."));
        tokenTransfer.init(obj, infos, gasPriceVal, needTransfer, needUpdate, outputFileName);

        tokenTransfer.process();

    }

    public void init(Map<String, Object> params, ArrayList<TransferInfo> infos, int gasPriceVal,
                     boolean needTransfer, boolean needUpdate, String output_file) {

        this.gasPrice = gasPriceVal;

        this.needTransfer = needTransfer;

        this.needUpdate = needUpdate;

        System.out.println("needTransfer:" + needTransfer + " needUpdate:" + needUpdate);

        this.infos = infos;

        Map<String, Object> web3_info = (Map<String, Object>) params.get("web3");

        url = (String) web3_info.get("url");

        w3jHelper = new Web3jHelper(url);

        int tempSleepDuration = (Integer) web3_info.get("sleepDuration");
        if (tempSleepDuration > 1000) {
            sleepDuration = tempSleepDuration;
        }

        if (gasPrice <= 0) {
            gasPrice = (Integer) web3_info.get("gasPrice");
        }

        gasPerTx = (Integer) web3_info.get("gasPerTx");
        fromAddress = (String) web3_info.get("address");
        chainId = ((Integer) web3_info.get("chainId")).byteValue();

        if (chainId == ChainId.MAINNET) {
            baseUrl = "https://www.etherscan.io/tx/";
        }

        this.outputFile = chainId + "_" + output_file;

        System.out.println("baseUrl:" + baseUrl);
        System.out.println("gasPrice:" + gasPrice);
        System.out.println("chainId:" + chainId);

        String privateKeyPath = (String) web3_info.get("privatekey_path");
        String password = (String) web3_info.get("password");

        try {
            File keyFile = new File(privateKeyPath);

            credentials = WalletUtils.loadCredentials(password, keyFile);

        } catch (Exception e) {
            System.err.println(e);
        }

        Map<String, Object> tokenInfo = (Map<String, Object>) params.get("token");
        contractAddress = (String) tokenInfo.get("token_address");
        System.out.println(contractAddress);

    }

    private void doCheckAndSend() {

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

                    for (ErrorInfo errInfo : errorInfos) {
                        System.out.println(errInfo);
                        errAmt += errInfo.getAmt();
                    }

                    System.exit(-1);
                }

                BigInteger totalBalance = w3jHelper.getTokenBalance(contractAddress, this.fromAddress);

                int offset = 4;

                int decimals = 18;

                double tb = totalBalance.divide(BigInteger.valueOf(10).pow(decimals - offset)).doubleValue() / (Math.pow(10, offset));

                String val = w3jHelper.getBalance(fromAddress).toString();
                System.out.println("val:" + val);

                double ethB = Convert.fromWei(val, Convert.Unit.ETHER).doubleValue();

                System.out.println("fromAddress:" + fromAddress + " totalBalance:" + String.format("%.04f", tb));
                System.out.println(String.format("get Eth Balance:%.04f", ethB));
                System.out.println(String.format("count = %d", infos.size()));
                System.out.println(String.format("%.04f", total_amt) + " to be sent!");

                extraInfos.put("totalBalance", String.format("%.04f", tb));

                extraInfos.put("total_amt", String.format("%.04f", total_amt));
                double gasEth = gasPrice * gasPerTx * 1e-9;

                System.out.println("all_amt:" + String.format("%.04f", (total_amt + errAmt)) + "   error amt:" + errAmt);
                System.out.println("total count:" + infos.size() + " txs.");
                System.out.println("eth per tx:" + String.format("%.06f", gasEth) + " ether");
                System.out.println("total gas(eth):" + String.format("%.06f", infos.size() * gasEth) + " ether.");

                extraInfos.put("total count", String.format("%d", infos.size()));
                extraInfos.put("gas(eth) per tx", String.format("%.06f", gasEth) + " wei");
                extraInfos.put("total gas(eth)", String.format("%.06f", gasEth * infos.size()) + " wei");

                if (ethB < gasEth * infos.size())  {
                    System.out.println("buffer account don't have enough eth for gas!");
                    System.exit(-1);
                }

                if (TokenTransfer.this.needTransfer) {

                    if (tb < total_amt) {
                        System.out.println("buffer account don't have enough token!");
                        System.exit(-1);
                    }

                    try {
                        System.out.println("transfer now ? (yes/NO)");
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                        String str = br.readLine();
                        if (!(str != null && CONFIRM.equals(str.toLowerCase()))) {
                            System.out.println("reject to transfer! exit!");
                            System.exit(0);
                        } else {
                            System.out.println("starting to transfer token!");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    BigInteger nonce = w3jHelper.getNonce(fromAddress);

                    for (TransferInfo info : infos) {

                        if (!"".equals(info.getFormattedEthAddress())) {

                            String txHash = w3jHelper.sendToken(info.getId(), credentials, nonce, chainId,
                                    gasPrice, contractAddress, info.getFormattedEthAddress(),
                                    info.getAmount(), decimals);

                            info.setTxHash(txHash);

                            System.out.println("just sent " + info.getAmount()
                                    + " token to " + info.getFormattedEthAddress()
                                    + " (" + (info.getId() + 1) + "/" + infos.size() + ").");

                            nonce = nonce.add(BigInteger.ONE);

                        }

                    }

                } else {
                    return;
                }

                long endTime = System.currentTimeMillis();

                long sendTime = (endTime - startTime);
                extraInfos.put("sendTime", String.format("%d", sendTime) + " ms");

                IOUtils.WriteResult(outputFile + "_txs.csv", infos, extraInfos);

                System.out.println("sendToken time:" + sendTime + "ms");

            }

        } catch (Exception e) {
            System.err.println(e);
        }

    }

    private void process() {

        doCheckAndSend();

        if (needUpdate) {
            updateStatusFromFile(outputFile + "_txs.csv");
        }

        if (w3jHelper != null) {
            w3jHelper.release();
        }

        System.out.println("-----run end!");

    }

    private void updateStatusFromFile(String path) {

        System.out.println("start updateStatusFromFile! path:" + path);

        EnumMap<IOUtils.Results, Object> results = IOUtils.getTxInfos(path);

        if (results == null) {
            System.out.println("results == null");
            return;
        }

        List<TransferInfo> infos = (List<TransferInfo>) results.get(IOUtils.Results.LST);
        Map<String, String> extraInfos = (Map<String, String>) results.get(IOUtils.Results.EXTRA);

        updateStatus(infos, extraInfos);
    }

    private void updateStatus(List<TransferInfo> infos, Map<String, String> extraInfos) {

        System.out.println("start updateStatus!");

        long startTime = System.currentTimeMillis();

        if (infos != null) {

            System.out.println("infos.size=" + infos.size());
            System.out.println("sleepDuration=" + sleepDuration);

            boolean allConfirmed = false;

            while (running && !allConfirmed) {

                try {
                    Thread.currentThread().sleep(sleepDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                allConfirmed = true;

                int remaining_count = infos.size();

                int c = 0;

                for (TransferInfo info : infos) {
                    c++;
                    if (info.getTxHash() != null && !"".equals(info.getTxHash())) {
                        if ("".equals(info.getStatus())) {
                            Optional<TransactionReceipt> receiptOptional = Optional.empty();
                            try {
                                receiptOptional = w3jHelper.sendTransactionReceiptRequest(info.getTxHash());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (receiptOptional.isPresent()) {
                                info.setStatus(receiptOptional.get().getStatus());
                                System.out.println("set status for " + c + ". " + info.getTxHash());
                                remaining_count--;
                            } else {
                                allConfirmed = false;
                                break;
                            }
                        } else {
                            remaining_count--;
                        }
                    }
                }

                if (allConfirmed) {
                    break;
                }
                System.out.println("remaining unconfirmed tx num:" + remaining_count);
            }
        }

        long endTime = System.currentTimeMillis();

        long updateTime = (endTime - startTime);
        extraInfos.put("updateTime", String.format("%d", updateTime) + " ms");

        System.out.println("updateStatus time:" + updateTime + "ms");

        IOUtils.WriteResult(outputFile + "_report.csv", infos, extraInfos);
    }

}
