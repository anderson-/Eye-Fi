package eyefi;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.jhlabs.image.BlurFilter;
import com.jhlabs.image.NoiseFilter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 *
 * @author Anderson Antunes
 */
interface ImageProvider {

    public BufferedImage getImage();

    public Object getObject();
}

public class EyeFi {

    public static class TimeoutException extends RuntimeException {

    }

    // capacity =  QR_CAP[version][errorCorrection]
    public static final int[][] QR_CAP = {
        {17, 14, 11, 7},
        {32, 26, 20, 14},
        {53, 42, 32, 24},
        {78, 62, 46, 34},
        {106, 84, 60, 44},
        {134, 106, 74, 58},
        {154, 122, 86, 64},
        {192, 152, 108, 84},
        {230, 180, 130, 98},
        {271, 213, 151, 119},
        {321, 251, 177, 137},
        {367, 287, 203, 155},
        {425, 331, 241, 177},
        {458, 362, 258, 194},
        {520, 412, 292, 220},
        {586, 450, 322, 250},
        {644, 504, 364, 280},
        {718, 560, 394, 310},
        {792, 624, 442, 338},
        {858, 666, 482, 382},
        {929, 711, 509, 403},
        {1003, 779, 565, 439},
        {1091, 857, 611, 461},
        {1171, 911, 661, 511},
        {1273, 997, 715, 535},
        {1367, 1059, 751, 593},
        {1465, 1125, 805, 625},
        {1528, 1190, 868, 658},
        {1628, 1264, 908, 698},
        {1732, 1370, 982, 742},
        {1840, 1452, 1030, 790},
        {1952, 1538, 1112, 842},
        {2068, 1628, 1168, 898},
        {2188, 1722, 1228, 958},
        {2303, 1809, 1283, 983},
        {2431, 1911, 1351, 1051},
        {2563, 1989, 1423, 1093},
        {2699, 2099, 1499, 1139},
        {2809, 2213, 1579, 1219},
        {2953, 2331, 1663, 1273}
    };

    public static final byte CMD_REQUEST_SECTION = 10;
    public static final byte CMD_SECTION = 15;
    public static final byte CMD_REQUEST_LIST = 20;
    public static final byte CMD_REQUEST_CHECKSUM = 33;
    public static final byte CMD_CHECKSUM = 22;
    public static final byte CMD_LIST = 21;
    public static final byte CMD_PING = 40;
    public static final byte CMD_PONG = 50;
    public static final byte CMD_REQUEST_END = 60;
    public static final byte CMD_END = 70;

    double rotation = 0;

    DecimalFormat df = new DecimalFormat("####0.00");

    Webcam webcam = null;

    int width = 1;
    int height = 1;

    int secondaryCounter = 0;

    int errorCorrectionLevel = 0;
    int QRSize = 200;
    int dataSendLength = 0;

    ArrayList<String> status = new ArrayList<>();
    int lineCount = 0;

    private byte[] dataRecived;
    private byte[] lastDataRecived;
    private final QRCodeMultiReader reader = new QRCodeMultiReader();

    int colorBar = 150;
    long readerTotalCounter = 0;
    long readerTempCounter = 0;
    long readerSucessCounter = 0;
    float conQuality = 0;

    boolean readOk = false;
    long error = 0;
    long maxError = 100;
    boolean harder;
    Result result = null;

    boolean fullscreen = true;
    boolean server;
    private BufferedImage qrCode;
    private ImageProvider imageProvider;
    private JFrame frame;
    int nMessages;
    int maxVersion;
    int minErrorCorrectionLevel;
    int maxErrorCorrectionLevel;
    int pingTimeout;
    boolean terminated = false;
    boolean inverse;
    boolean rotate = false;
    boolean noise = false;
    int[] parameters;
    private boolean interference = false;
    int newTransferVersion = 0;
    boolean imageNotAvailable = false;
    private boolean noTimeout = false;

    public EyeFi(int nMessages, int maxVersion, int minErrorCorrectionLevel, int maxErrorCorrectionLevel, int pingTimeout, boolean inverse, int[] parameters) {
        this.nMessages = nMessages;
        this.maxVersion = maxVersion;
        this.minErrorCorrectionLevel = minErrorCorrectionLevel;
        this.maxErrorCorrectionLevel = maxErrorCorrectionLevel;
        this.pingTimeout = pingTimeout;
        this.inverse = inverse;
        this.parameters = parameters;

        //Rotate image to provide diversity to the reader.
        //It reduces the throwing of ChecksumException and FormatException
        //for some reason that I dont know why...
        new Thread("Rotate") {
            int i = 0;

            @Override
            public void run() {
                while (!terminated) {
                    rotation += (rotate) ? Math.PI / 2 : 0;
                    i++;
                    switch (i % 7) {
                        case 0:
                            rotation += .04;
                            break;
                        case 2:
                            rotation += .02;
                            break;
                        case 4:
                            rotation -= .02;
                            break;
                        case 6:
                            rotation -= .04;
                            break;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (Exception e) {

                    }
                }
            }
        }.start();
    }

    public void run() {
        createWindow();
    }

    public void initWebcam(final int index, final Dimension videoSize) {
        new Thread() {
            @Override
            public void run() {
                Webcam w;
                if (index < 0) {
                    w = Webcam.getDefault();
                } else {
                    w = Webcam.getWebcams().get(index);
                }
                w.setViewSize(videoSize);
                w.open();
                webcam = w;
            }
        }.start();
        setImageProvider(new eyefi.ImageProvider() {
            private BufferedImage img = null;

            {
                new Thread() {
                    @Override
                    public void run() {
                        while (!terminated) {
                            if (webcam != null) {
                                img = shake(webcam.getImage());
                            }
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ex) {

                            }
                        }
                    }
                }.start();
            }

            @Override
            public BufferedImage getImage() {
                return img;
            }

            @Override
            public Object getObject() {
                return webcam;
            }
        });
    }

    private JFrame getFrame() {
        return frame;
    }

    public static void main(String[] args) {

        boolean silent = false;
        boolean simulate = false;
        String filePath = "img/tux.png";
        //String filePath = null;
        boolean fullscreen = true;
        int maxMessages = 3;
        int maxVersion = QR_CAP.length - 1;
        int minECL = 0;
        int maxECL = QR_CAP[0].length - 1;
        int pingTimeout = 5000;
        boolean inverse = false;
        int[] parameters = null;
        boolean rotate = false;

        for (Iterator<String> it = Arrays.asList(args).iterator(); it.hasNext();) {
            String arg = it.next();
            switch (arg) {
                case "-h":
                case "--help":
                    System.out.println("Eye-Fi: Notebook file transfer through QR codes, using only the screen and webcam.");
                    System.out.println();
                    System.out.println("Conceived and developed by Anderson Antunes");
                    System.out.println("GitHub: https://github.com/anderson-/eye-fi");
                    System.out.println();
                    System.out.println("Usage:");
                    System.out.println("  java -jar Eye-Fi.jar [OPTION...]");
                    System.out.println("  or");
                    System.out.println("  ./Eye-Fi.jar [OPTION...]");
                    System.out.println();
                    System.out.println("Application Options:");
                    System.out.println("  -h, --help       \tShow this help page");
                    System.out.println("  -r, --receiveMode\tSet receive mode");
                    System.out.println("  -w, --windowed   \tSet windowed mode");
                    System.out.println("  -f, --file       \tSet file to send");
                    System.out.println("  -V, --maxVersion \tSet max QR code version [0 to 39]");
                    System.out.println("  -e, --minECL     \tSet maximum QR code error correction level [0 to 3]");
                    System.out.println("  -E, --maxECL     \tSet minimum QR code error correction level [0 to 3]");
                    System.out.println("  -m, --maxMessages\tSet maximum number of messages per test on benchmark [>=1]");
                    System.out.println("  -s, --silent     \tDisable input dialogs");
                    System.out.println("  -R, --rotate     \tRotate webcam image");
                    System.out.println("  -S, --simulate   \tSimulate a file transfer between two notebooks");
                    System.out.println("  -p, --pingTimeout\tSet ping timeout");
                    System.out.println("  -i, --inversePing\tStart from maximal QR code version and decrease it while benchmarking");
                    System.out.println("  -P, --parameters \tSet transfer parameters: Version [0 to 39] and ECL [0 to 3]");
                    System.out.println();
                    System.out.println("Keyboard shortcuts:");
                    System.out.println("  L                \tFull log window pop-up");
                    System.out.println("  1                \tSimulate inteference on client webcam");
                    System.out.println("  2                \tSimulate inteference on server webcam");
                    System.out.println("  PLUS/EQUALS      \tManually increase QR code version");
                    System.out.println("  MINUS            \tManually decrease QR code version");
                    System.out.println("  BACKSPACE        \tToggle timeout");
                    System.out.println("  ESCAPE           \tExit");
                    System.out.println();
                    System.out.println("Argument examples:");
                    System.out.println("  -f img/tux.png -s      \tSend file without dialogs");
                    System.out.println("  -r -P 5 0 -s           \tReceive file without dialogs with QR code v5 and ECL 0");
                    System.out.println("  -S -e 1 -E 1 -m 2 -V 39\tLocal simulation");
                    System.exit(0);
                    break;
                case "-w":
                case "--windowed":
                    fullscreen = false;
                    break;
                case "-f":
                case "--file":
                    filePath = it.next();
                    break;
                case "-V":
                case "--maxVersion":
                    maxVersion = Integer.parseInt(it.next());
                    if (maxVersion >= QR_CAP.length) {
                        maxVersion = QR_CAP.length - 1;
                    }
                    break;
                case "-e":
                case "--minECL":
                    minECL = Integer.parseInt(it.next());
                    if (minECL < 0) {
                        minECL = 0;
                    }
                    break;
                case "-E":
                case "--maxECL":
                    maxECL = Integer.parseInt(it.next());
                    if (minECL >= QR_CAP[0].length) {
                        minECL = QR_CAP[0].length - 1;
                    }
                    break;
                case "-m":
                case "--maxMessages":
                    maxMessages = Integer.parseInt(it.next());
                    break;
                case "-s":
                case "--silent":
                    silent = true;
                    break;
                case "-R":
                case "--rotate":
                    rotate = true;
                    break;
                case "-S":
                case "--simulate":
                    simulate = true;
                    break;
                case "-r":
                case "--receiveMode":
                    filePath = null;
                    break;
                case "-p":
                case "--pingTimeout":
                    pingTimeout = Integer.parseInt(it.next());
                    break;
                case "-i":
                case "--inversePing":
                    inverse = true;
                    break;
                case "-P":
                case "--parameters":
                    parameters = new int[]{Integer.parseInt(it.next()), Integer.parseInt(it.next()), 0};
                    break;
                default:
                    System.out.println("Invalid Argument");
                    System.exit(0);
            }
        }

        if (!simulate) {
            EyeFi qrcomm = new EyeFi(maxMessages, maxVersion, minECL, maxECL, pingTimeout, inverse, parameters);
            qrcomm.initWebcam(-1, WebcamResolution.VGA.getSize());
            qrcomm.setFullScreen(fullscreen);
            qrcomm.run();
            qrcomm.rotate = rotate;
            ImageIcon icon = new ImageIcon(EyeFi.class.getResource("/eyefi/slogo64.png"));
            if (!silent) {
                String[] buttons = new String[]{"Send file", "Receive file"};
                int rc = JOptionPane.showOptionDialog(null, "Notebook file transfer through QR codes,\nusing only the screen and webcam.\n\nhttps://github.com/anderson-/eye-fi\n\nBy Anderson Antunes\n\nWhat you want to do?", "Welcome to Eye-Fi!",
                        JOptionPane.DEFAULT_OPTION, 0, icon, buttons, buttons[1]);
                if (rc == 0) {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Choose a file to send");
                    int returnVal = chooser.showOpenDialog(qrcomm.getFrame());
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        filePath = chooser.getSelectedFile().getAbsolutePath();
                    }
                } else if (rc == 1) {
                    filePath = null;
                    buttons = new String[]{"Default", "Inverse", "Manual"};
                    rc = JOptionPane.showOptionDialog(null, "Choose config mode:", "Transfer Rate Benchmark",
                            JOptionPane.DEFAULT_OPTION, 0, icon, buttons, buttons[1]);
                    if (rc == 0) {
                        qrcomm.inverse = false;
                        Integer[] values = new Integer[20];
                        for (int i = 0; i < 20; i++) {
                            values[i] = (i + 1) * 500;
                        }
                        qrcomm.pingTimeout = (Integer) JOptionPane.showInputDialog(null, "Select the ping timeout in milliseconds:", "Ping timeout", JOptionPane.QUESTION_MESSAGE, icon, values, values[7]);
                    } else if (rc == 1) {
                        qrcomm.inverse = true;
                        Integer[] values = new Integer[20];
                        for (int i = 0; i < 20; i++) {
                            values[i] = (i + 1) * 500;
                        }
                        qrcomm.pingTimeout = (Integer) JOptionPane.showInputDialog(null, "Select the ping timeout in milliseconds:", "Ping timeout", JOptionPane.QUESTION_MESSAGE, icon, values, values[7]);
                    } else {
                        Integer[] values = new Integer[20];
                        for (int i = 0; i < 20; i++) {
                            values[i] = (i + 1) * 500;
                        }
                        qrcomm.pingTimeout = (Integer) JOptionPane.showInputDialog(null, "Select the ping timeout in milliseconds:", "Ping timeout", JOptionPane.QUESTION_MESSAGE, icon, values, values[7]);
                        parameters = new int[]{0, 0, 0};
                        values = new Integer[40];
                        for (int i = 0; i < 40; i++) {
                            values[i] = i;
                        }
                        parameters[0] = (Integer) JOptionPane.showInputDialog(null, "Select Q RCode version:", "Manual transfer rate", JOptionPane.QUESTION_MESSAGE, icon, values, values[0]);
                        values = new Integer[4];
                        for (int i = 0; i < 4; i++) {
                            values[i] = i;
                        }
                        parameters[1] = (Integer) JOptionPane.showInputDialog(null, "Select QR Code error correction level:", "Manual transfer rate", JOptionPane.QUESTION_MESSAGE, icon, values, values[0]);
                        qrcomm.parameters = parameters;
                    }
                }
            }
            if (filePath != null && !filePath.isEmpty()) {
                qrcomm.serverMode(filePath);
            } else {
                File file = qrcomm.receiveMode();
                if (!silent) {
                    String[] buttons = new String[]{"Yes", "No"};
                    int rc = JOptionPane.showOptionDialog(null, "Do you want to open received file with\nthe default associated program?", "Open received file",
                            JOptionPane.DEFAULT_OPTION, 0, icon, buttons, buttons[1]);
                    if (rc == 0) {
                        try {
                            Desktop.getDesktop().open(file);
                        } catch (IOException ex) {
                        }
                    }
                } else {
                    if (isFileImage(file)) {
                        qrcomm.showPictureFrame(file, file.getName());
                    } else {
                        qrcomm.log("No visualization available.");
                    }
                }
            }
        } else {
            final String path = filePath;
            final EyeFi server = new EyeFi(maxMessages, maxVersion, minECL, maxECL, pingTimeout, inverse, parameters);
            final EyeFi client = new EyeFi(maxMessages, maxVersion, minECL, maxECL, pingTimeout, inverse, parameters);
            server.setFullScreen(false);
            client.setFullScreen(false);
            server.setImageProvider(client.createImageProvider());
            client.setImageProvider(server.createImageProvider());
            //test using two screens and two webcams on the same computer
//            server.initWebcam(1, WebcamResolution.QVGA.getSize());
//            client.initWebcam(0, WebcamResolution.QVGA.getSize());
            server.run();
            client.run();
            server.rotate = true;
            client.rotate = true;
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            double width = screenSize.getWidth();
            double height = screenSize.getHeight();
            client.getFrame().setSize((int) width, (int) height / 2);
            client.getFrame().setLocation(0, 0);
            server.getFrame().setSize((int) width, (int) height / 2);
            server.getFrame().setLocation(0, (int) height / 2);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
            new Thread("Server") {
                @Override
                public void run() {
                    server.serverMode(path);
                }
            }.start();
            new Thread("Client") {
                @Override
                public void run() {
                    File file = client.receiveMode();
                    if (isFileImage(file)) {
                        client.showPictureFrame(file, file.getName());
                    } else {
                        client.log("No visualization available.");
                    };
                }
            }.start();
        }
    }

    public void send(byte... data) {
        makeQRCode(data);
    }

    public byte[] read(int timeout) {
        if (timeout == 0 || noTimeout) {
            return read();
        }
        long t = System.currentTimeMillis();
        while (System.currentTimeMillis() - t < timeout || noTimeout) {
            BufferedImage image = imageProvider.getImage();
            if (image != null) {
                readQRCode(image);
                if (readOk) {
                    readOk = false;
                    return dataRecived;
                } else {
                    dataRecived = null;
                }
            }
            try {
                Thread.sleep(5);
            } catch (Exception e) {

            }
        }
        throw new TimeoutException();
    }

    public byte[] read() {
        while (true) {
            BufferedImage image = imageProvider.getImage();
            if (image != null) {
                readQRCode(image);
                if (readOk) {
                    readOk = false;
                    return dataRecived;
                } else {
                    dataRecived = null;
                }
            }
            try {
                Thread.sleep(5);
            } catch (Exception e) {

            }
        }
    }

    public void serverMode(String filePath) {
        frame.setTitle("Server");
        try {
            if (filePath == null || filePath.isEmpty()) {

            }
            Path path = Paths.get(filePath);
            byte[] fileArray = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            byte[] nameArray = fileName.getBytes();
            int fileNameLength = nameArray.length;
            int dataLen = fileArray.length + nameArray.length;
            byte[] data;
            {
                ByteBuffer buffer = ByteBuffer.allocate(dataLen);
                buffer.put(nameArray);
                buffer.put(fileArray);
                data = buffer.array();
            }
            log("Loaded \"" + fileName + "\"[" + fileNameLength + "], providing " + dataLen + " Bytes.");
            long t = System.currentTimeMillis();
            float ping = 0;
            int it = 0;
            while (true) {
                read();
                if (dataRecived != null) {
                    ping = incrementalAverageIteration(ping, (System.currentTimeMillis() - t), it);
                    t = System.currentTimeMillis();
                    it++;
                    switch (dataRecived[0]) {
                        case CMD_PING:
                            log("Responding to ping(" + dataRecived[1] + "," + dataRecived[2] + "), " + QR_CAP[dataRecived[1]][dataRecived[2]] + " bytes sent");
                            send(genByteArray(QR_CAP[dataRecived[1]][dataRecived[2]], true, CMD_PONG, dataRecived[1], dataRecived[2]));
                            break;
                        case CMD_REQUEST_LIST:
                            log("Sending data info...");
                            send(CMD_LIST, (byte) fileNameLength, (byte) (dataLen >>> 24), (byte) (dataLen >>> 16), (byte) (dataLen >>> 8), (byte) dataLen);
                            break;
                        case CMD_REQUEST_CHECKSUM:
                            byte[] md5 = processChecksum(new File(filePath));
                            byte[] msg = new byte[1 + md5.length];
                            msg[0] = CMD_CHECKSUM;
                            System.arraycopy(md5, 0, msg, 1, md5.length);
                            log("Sending file checksum: " + getHexString(md5));
                            send(msg);
                            break;
                        case CMD_REQUEST_SECTION:
                            int index;
                             {
                                ByteBuffer buffer = ByteBuffer.allocate(4);
                                buffer.clear();
                                buffer.put(dataRecived, 1, 4);
                                buffer.flip();
                                index = buffer.getInt();
                            }
                            int version = dataRecived[5];
                            errorCorrectionLevel = dataRecived[6];
                            int counterByteSize = dataRecived[7];
                             {
                                ByteBuffer buffer = ByteBuffer.allocate(QR_CAP[version][errorCorrectionLevel]);
                                buffer.clear();
                                buffer.put(CMD_SECTION);
                                int tmpIndex = index;
                                for (int i = 0; i < counterByteSize; i++) {
                                    buffer.put((byte) tmpIndex);
                                    tmpIndex >>>= 8;
                                }
                                int length = QR_CAP[version][errorCorrectionLevel] - (1 + counterByteSize);
                                if (index + length > data.length) {
                                    length = data.length - index;
                                }
                                buffer.put(data, index, length);
                                buffer.flip();
                                send(buffer.array());
                                log("Sending section " + index + " [" + df.format(((index + length) / (float) fileArray.length) * 100f) + "%]" + " of " + length + " Bytes (" + df.format((length / (float) fileArray.length) * 100f) + "%).");
                                log("Ping: " + (int) ping + "ms. " + (int) ((fileArray.length / length * ping) / 1000f) + "s needed to send file.");
                            }
                            break;
                        case CMD_REQUEST_END:
                            send(genByteArray(7, true, CMD_END));
                            frame.dispose();
                            log("THE END");
                            terminated = true;
                            return;
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }
            }
        } catch (IOException ex) {
            throw new Error(ex);
        }
    }

    public File receiveMode() {
        frame.setTitle("Client");
        if (parameters == null) {
            log("Benchmarking...");
            parameters = getOptimalTransferParameters(nMessages, maxVersion, minErrorCorrectionLevel, maxErrorCorrectionLevel, pingTimeout, inverse);
        }
        boolean isChecksumValid;
        String filename;
        File file;
        do {
            log("------------------------");
            log("Requesting data info...");
            int[] dataPos = getDataDistribution();
            log("Requesting filename...");
            filename = readString(0, dataPos[0], parameters[0], parameters[1]);
            int filesize = dataPos[1] - dataPos[0];
            int qrcodecap = QR_CAP[parameters[0]][parameters[1]] - (getNumberOfBytesToSave(filesize) + 1);
            log("Starting file transfer...");
            log("------------------------");
            log("   > File name: \"" + filename + "\"");
            log("   > File size: " + (filesize > 1024 ? filesize / 1024f + " kBytes" : filesize + " Bytes"));
            log("   > Transfer parameters:");
            log("        > QRCode version: " + parameters[0]);
            log("        > QRCode error correction level [L,M,Q,H]: " + parameters[1]);
            log("        > QRCode capacity: " + (qrcodecap > 1024 ? qrcodecap / 1024f + " kBytes" : qrcodecap + " Bytes"));
            if (parameters[2] > 0) {
                float bps = (parameters[2] * (float) qrcodecap / (float) QR_CAP[parameters[0]][parameters[1]]);
                float aproxTransferTime = filesize / bps;
                log("        > Transfer rate: ~ " + (bps > 1024 ? df.format(bps / 1024f) + " kB/s" : bps + " B/s"));
                log("        > Transfer time: ~ " + (aproxTransferTime > 60 ? (int) Math.floor(aproxTransferTime / 60f) + " min" : df.format(aproxTransferTime) + "s"));
            }
            log("------------------------");
            long t = System.currentTimeMillis();
            file = receiveFile(filename, dataPos[0], filesize, parameters[0], parameters[1]);
            float seconds = ((System.currentTimeMillis() - t) / 1000f);
            logOver("\"" + filename + "\" transfered in "
                    + (seconds > 60 ? df.format(seconds / 60f) + " min" : df.format(seconds) + "s") + " at "
                    + ((filesize / 1024f) / seconds > 1 ? df.format((filesize / 1024f) / seconds) + " kB/s" : df.format(filesize / seconds) + " B/s"));
            log("------------------------");
            isChecksumValid = fileChecksum(file);
            log("Checksum valid? " + (isChecksumValid ? "Yes" : "No"));
        } while (!isChecksumValid && false);
        file = copyFile(file, new File("received" + filename));
        log("------------------------");
        log("File saved: " + file.getAbsolutePath());
        return file;
    }

    public static boolean isFileImage(File file) {
        try {
            return (ImageIO.read(file) != null);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public void showPictureFrame(File file, String title) {
        try {
            JFrame picFrame = new JFrame(title);
            picFrame.add(new JLabel(new ImageIcon(ImageIO.read(file))));
            picFrame.pack();
            picFrame.setSize(new Dimension(picFrame.getWidth() + 200, picFrame.getHeight() + 200));
            picFrame.setLocationRelativeTo(null);
            picFrame.setVisible(true);
        } catch (Exception ex) {
            log("Error while displaying image: " + ex.getMessage());
        }
    }

    public boolean fileChecksum(File file) {
        send(genByteArray(7, true, CMD_REQUEST_CHECKSUM));
        read();
        if (dataRecived != null && dataRecived[0] == CMD_CHECKSUM) {
            byte[] remote = new byte[16];
            System.arraycopy(dataRecived, 1, remote, 0, 16);
            byte[] local = processChecksum(file);
            log("Remote file checksum: " + getHexString(remote));
            log("Local file checksum:  " + getHexString(local));
            return Arrays.equals(local, remote);
        }
        return false;
    }

    public int[] getDataDistribution() {
        send(genByteArray(7, true, CMD_REQUEST_LIST));
        read();
        if (dataRecived != null && dataRecived[0] == CMD_LIST) {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.clear();
            buffer.put(dataRecived, 2, 4);
            buffer.flip();
            int[] dataDis = new int[]{dataRecived[1], buffer.getInt()};
            return dataDis;
        }
        return null;
    }

    public int getNumberOfBytesToSave(int number) {
        return (int) Math.floor((31 - Integer.numberOfLeadingZeros(number)) / 8f) + 1;
    }

    public byte[] readSection(int start, int size, int version, int errorCorrection, boolean progress) {
        newTransferVersion = version;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.clear(); //prepare buffer to fill with data.
        int counterByteSize = getNumberOfBytesToSave(size);
        int qrCodeCapacity = QR_CAP[version][errorCorrection] - (1 + counterByteSize);
        int originalVersion = version;
        boolean usingLowerVersion = false;
        long startTime = System.currentTimeMillis();
        float bps = 0;
        if (progress) {
            log("");
        }
        int lostPackages = 0;
        for (int i = start; i < start + size; i += qrCodeCapacity) {
            long t = System.currentTimeMillis();
            if (newTransferVersion != version && !usingLowerVersion) {
                if (progress) {
                    if (newTransferVersion > version) {
                        log("User increreased QR code version to " + newTransferVersion + ".");
                        log("");
                    } else {
                        log("User decreased QR code version to " + newTransferVersion + ".");
                        log("");
                    }
                }
                version = newTransferVersion;
                originalVersion = newTransferVersion;
                qrCodeCapacity = QR_CAP[version][errorCorrection] - (1 + counterByteSize);
            }
            send(CMD_REQUEST_SECTION, (byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i, (byte) version, (byte) errorCorrection, (byte) counterByteSize);
            try {
                read(pingTimeout);
            } catch (TimeoutException e) {
                if (version > 0) {
                    version--;
                    usingLowerVersion = true;
                } else {
                    version = 1;
                    originalVersion = 1;
                    usingLowerVersion = false;
                }
                qrCodeCapacity = QR_CAP[version][errorCorrection] - (1 + counterByteSize);
                if (progress) {
                    log("Lost package " + i + ". Decreasing QR code version to " + version + ".");
                }
                i -= qrCodeCapacity;
                lostPackages++;
                continue;
            }
            if (dataRecived != null && dataRecived[0] == CMD_SECTION) {
                int index = 0;
                for (int j = counterByteSize - 1; j >= 0; j--) {
                    index <<= 8;
                    index |= (dataRecived[j + 1] & 0xFF);
                }
                if (i == index) {
                    int offset = 1 + counterByteSize;
                    int length = QR_CAP[version][errorCorrection] - offset;

                    if (buffer.remaining() < length) {
                        length = buffer.remaining();
                    }
                    buffer.put(dataRecived, offset, length);
                    if (usingLowerVersion) {
                        boolean versionIncreased = false;
                        if (lostPackages > 5) {
                            lostPackages = 0;
                            originalVersion--;
                        } else {
                            version++;
                            versionIncreased = true;
                        }
                        usingLowerVersion = (version != originalVersion);
                        i += qrCodeCapacity;
                        qrCodeCapacity = QR_CAP[version][errorCorrection] - (1 + counterByteSize);
                        i -= qrCodeCapacity;
                        if (progress && versionIncreased) {
                            log("QR code version increased to " + version + ".");
                            log("");
                        }
                    }
                    if (progress) {
                        float v = (length / ((System.currentTimeMillis() - t) / 1000f));
                        float time = (System.currentTimeMillis() - startTime) / 1000f;
                        bps = incrementalAverageIteration(bps, v, 10);//(i - start) / qrCodeCapacity
                        float seconds = (size - (i + length)) / bps;
                        logOver("Transfer " + df.format(100f - 100 * ((float) buffer.remaining() / size)) + "% complete, "
                                //                                + "remaining " + buffer.remaining() + " Bytes, "
                                + "velocity " + (bps / 1024f > 1 ? df.format(bps / 1024f) + " kB/s" : df.format(bps) + " B/s")
                                + ". " + (seconds > 60 ? df.format(seconds / 60f) + " min" : df.format(seconds) + "s") + " left."
                                + " Time elapsed: " + (time > 60 ? df.format(time / 60f) + " min" : df.format(time) + "s")
                        );
                    }
                }
            }
        }
        buffer.flip();
        return buffer.array();
    }

    public String readString(int start, int size, int version, int errorCorrection) {
        byte[] data;
        do {
            data = readSection(start, size, version, errorCorrection, false);
            version = version > 0 ? version - 1 : version;
        } while (data == null);
        return new String(data);
    }

    public File receiveFile(String name, int start, int size, int version, int errorCorrection) {
        try {
            byte[] data;
            do {
                data = readSection(start, size, version, errorCorrection, true);
                if (data == null) {
                    version = version > 0 ? version - 1 : version;
                    log("Restarting transfer using QRCode version " + version + " with " + QR_CAP[version][errorCorrection] + " Bytes...");
                }
            } while (data == null);
            File tempFile = File.createTempFile(name, "", null);
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(data);
            fos.close();
            return tempFile;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static File copyFile(File in, File out) {
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inChannel = new FileInputStream(in).getChannel();
            outChannel = new FileOutputStream(out).getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (IOException e) {

        } finally {
            if (inChannel != null) {
                try {
                    inChannel.close();
                } catch (IOException ex) {
                }
            }
            if (outChannel != null) {
                try {
                    outChannel.close();
                } catch (IOException ex) {
                }
            }
        }
        return out;
    }

    public float ping(int version, int errorCorrection, int timeout) {
        send(genByteArray(7, true, CMD_PING, (byte) version, (byte) errorCorrection));
        long t = System.currentTimeMillis();
        read(timeout);
        if (dataRecived != null && dataRecived[0] == CMD_PONG
                && dataRecived[1] == version
                && dataRecived[2] == errorCorrection) {
            long ping = System.currentTimeMillis() - t;
            int capacity = QR_CAP[version][errorCorrection];
            return capacity * (1000f / ping);
        }
        return 0;
    }

    public float benchmark(int version, int errorCorrection, int nMessages, int timeout) {
        float averagePing = 0;
        for (int i = 0; i < nMessages; i++) {
            averagePing = incrementalAverageIteration(averagePing, ping(version, errorCorrection, timeout), i);
        }
        return averagePing;
    }

    public int[] getOptimalTransferParameters(int nMessages, int maxVersion, int minErrorCorrectionLevel, int maxErrorCorrectionLevel, int timeout, boolean inverse) {
        int[] ret = new int[]{0, 0, 0};
        float max = 0;
        try {
            if (inverse) {
                for (int v = maxVersion; v >= 0; v--) {
                    for (int ec = minErrorCorrectionLevel; ec <= maxErrorCorrectionLevel; ec++) {
                        int tmpTimeout;
                        if (v == 0) {
                            tmpTimeout = 0;
                        } else if (v <= 3) {
                            tmpTimeout = 2 * timeout;
                        } else {
                            tmpTimeout = timeout;
                        }
                        float res = benchmark(v, ec, nMessages, tmpTimeout);
                        log("ping(" + v + "," + ec + "): " + (int) res + " B/s");
                        if (res > max) {
                            max = res;
                            ret[0] = v;
                            ret[1] = ec;
                        }
                        if (res < max / 2) {
                            throw new TimeoutException();
                        }
                    }
                }
            } else {
                for (int v = 0; v <= maxVersion; v++) {
                    for (int ec = minErrorCorrectionLevel; ec <= maxErrorCorrectionLevel; ec++) {
                        int tmpTimeout;
                        if (v == 0) {
                            tmpTimeout = 0;
                        } else if (v <= 3) {
                            tmpTimeout = 2 * timeout;
                        } else {
                            tmpTimeout = timeout;
                        }
                        float res = benchmark(v, ec, nMessages, tmpTimeout);
                        log("ping(" + v + "," + ec + "): " + (int) res + " B/s");
                        if (res > max) {
                            max = res;
                            ret[0] = v;
                            ret[1] = ec;
                        }
                    }
                }
            }
        } catch (TimeoutException e) {
            ret[0]--;
        }
        log("Best: ping(" + ret[0] + "," + ret[1] + ") at " + ((max / 1024f > 1f) ? df.format(max / 1024f) + " kB/s" : df.format(max) + " B/s"));
        ret[2] = (int) max;
        return ret;
    }

    public void terminate() {
        send(genByteArray(7, true, CMD_REQUEST_END));
        read();
        if (dataRecived != null && dataRecived[0] == CMD_END) {
            log("THE END");
            frame.dispose();
            terminated = true;
        }
    }

    public byte randByte() {
        return (byte) (256 * Math.random());
    }

    public byte[] genByteArray(int length, boolean rand, byte... values) {
        byte[] array = new byte[length];
        int i;
        for (i = 0; i < values.length; i++) {
            array[i] = values[i];
        }
        if (rand) {
            for (; i < length; i++) {
                array[i] = (byte) (256 * Math.random());
            }
        } else {
            for (; i < length; i++) {
                array[i] = 0;
            }
        }
        return array;
    }

    private void setImageProvider(ImageProvider imageProvider) {
        this.imageProvider = imageProvider;
    }

    private void setFullScreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public final void createWindow() {
        frame = new JFrame();

        final JPanel panel = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {

                Graphics2D g2 = (Graphics2D) g;

                width = getWidth();
                height = getHeight();

                g2.setStroke(new BasicStroke(2));

                g2.setColor(Color.black);
                g2.fillRect(0, 0, height, height);

                g2.drawImage(qrCode, 6, 6, height - 12, height - 12, Color.BLACK, null);

                g2.fillRect(height, 0, width - height, height);

                g2.setFont(new Font("Monospaced", Font.BOLD, 14));

                g2.setColor(new Color(240, 240, 240));
                g2.drawString("Error Correction Level : "
                        + ((errorCorrectionLevel == 0) ? "L"
                                : (errorCorrectionLevel == 1) ? "M"
                                        : (errorCorrectionLevel == 2) ? "Q"
                                                : (errorCorrectionLevel == 3) ? "H"
                                                        : "err"), 10, 25);
                g2.drawString("Data Length(bytes) : " + dataSendLength, 10, height - 15);

                g2.setColor(Color.white);
                g2.drawLine(height + 1, 0, height + 1, height);
                g2.drawLine(height + 3, 0, height + 3, height);

                {
                    final int stringPosX = 20;
                    int y = 20;
                    for (int i = status.size() - 16; i < status.size(); i++) {
                        if (i >= 0) {
                            String line = status.get(i);
                            int stringWidth = g.getFontMetrics().stringWidth(line);
                            boolean wrapped = false;
                            if (stringWidth + stringPosX > width - height) {
                                for (int k = line.length() - 1; k >= 0; k--) {
                                    char c = line.charAt(k);
                                    if (c == '.' || c == ' ') {
                                        if (g.getFontMetrics().stringWidth(line.substring(0, k)) + stringPosX < width - height) {
                                            wrapped = true;
                                            g2.drawString(line.substring(0, k), height + stringPosX, y += g.getFontMetrics().getHeight());
                                            g2.drawString(line.substring(k).trim(), height + stringPosX, y += g.getFontMetrics().getHeight());
                                            break;
                                        }
                                    }
                                }
                                if (!wrapped) {
                                    for (int k = line.length() - 1; k >= 0; k--) {
                                        if (g.getFontMetrics().stringWidth("...") + g.getFontMetrics().stringWidth(line.substring(0, k)) + stringPosX < width - height) {
                                            g2.drawString(line.substring(0, k) + "...", height + stringPosX, y += g.getFontMetrics().getHeight());
                                            break;
                                        }
                                    }
                                }
                            } else {
                                g2.drawString(line, height + stringPosX, y += g.getFontMetrics().getHeight());
                            }
                        }
                    }
                }
                boolean img = false;
                if (imageProvider != null && imageProvider.getImage() != null) {
                    g2.setColor(Color.white);
                    if (imageProvider.getObject() instanceof Webcam) {
                        Webcam webcam = (Webcam) imageProvider.getObject();
                        g2.drawString("fps : " + (int) webcam.getFPS(), width - 140, height - 125);
                    }
                    g2.drawImage(imageProvider.getImage(), width - 160, height - 120, 160, 120, Color.black, null);
                    if (result != null) {
                        Result r = result;
                        if (harder) {
                            g2.setColor(Color.red);
                        } else {
                            g2.setColor(Color.green);
                        }

                        int x = 0, y = 0, tx = 0, ty = 0, fx = 0, fy = 0;
                        g2.setColor(Color.red);
                        g2.setStroke(new BasicStroke(5));
                        for (ResultPoint p : r.getResultPoints()) {
                            tx = (int) p.getX();
                            ty = (int) p.getY();
                            tx = (int) map(tx, 0, imageProvider.getImage().getWidth(), 0, 160) + (width - 160);
                            ty = (int) map(ty, 0, imageProvider.getImage().getHeight(), 0, 120) + (height - 120);
                            if (x != 0) {
                                g2.drawLine(x, y, tx, ty);
                            } else {
                                fx = tx;
                                fy = ty;
                            }
                            x = (int) p.getX();
                            y = (int) p.getY();
                            x = (int) map(x, 0, imageProvider.getImage().getWidth(), 0, 160) + (width - 160);
                            y = (int) map(y, 0, imageProvider.getImage().getHeight(), 0, 120) + (height - 120);
//                            g2.fillRect((int) map(p.getX(), 0, imageProvider.getImage().getWidth(), 0, 160) + (width - 160) - 7, (int) map(p.getY(), 0, imageProvider.getImage().getHeight(), 0, 120) + (height - 120) - 7, 14, 14);
                        }
                        g2.drawLine(x, y, fx, fy);
                    }
                    img = true;
                }

                if (!img || interference) {
                    int x = width - 160;
                    int y = height - 120;
                    int w = 160;
                    int h = 120;
                    if (interference) {
                        g2.setColor(new Color(40, 10, 10, 230));
                        g2.fillRect(x, y, w, h);
                        g2.setColor(Color.red);
                        g2.drawString("Interference On", x + w / 2 - 60, y + h / 2 + 5);
                    } else {
                        g2.setColor(Color.darkGray);
                        g2.drawRect(x, y, w, h);
                        g2.drawLine(x, y, x + w, y + h);
                        g2.drawLine(x, y + h, x + w, y);
                        g2.setColor(Color.white);
                        g2.drawString("Waiting...", x + w / 2 - 35, y + h / 2 + 5);
                    }
                }

                if (maxError > 0) {
                    g2.setColor(Color.getHSBColor(colorBar / 360f, 1, 1));
                    g2.fillRect(width - 160, height - 120, 5, (int) ((120.0 / maxError) * error));
                    colorBar = (colorBar > 0) ? 150 - (int) ((151.0 / maxError) * error) : 150;
                }

                g2.setColor(Color.decode("#31F0F5"));
                g2.fillRect(width - 5, height - 120, 5, (int) ((120.0 / 100) * conQuality));

                g2.dispose();
            }
        };

        new Thread() {
            @Override
            public void run() {
                while (!terminated) {
                    panel.repaint();
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }.start();

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new KeyEventDispatcher() {

            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    /*
                     For key pressed and key released events, 
                     the getKeyCode method returns the event's
                     keyCode. For key typed events, the getKeyCode 
                     method always returns VK_UNDEFINED.
                     */
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ESCAPE:
                            System.exit(0);
                            break;
                        case KeyEvent.VK_1:
                            if (frame.getTitle().equals("Client")) {
                                interference = !interference;
                            }
                            break;
                        case KeyEvent.VK_2:
                            if (frame.getTitle().equals("Server")) {
                                interference = !interference;
                            }
                            break;
                        case KeyEvent.VK_BACK_SPACE:
                            noTimeout = !noTimeout;
                            log("Timeout " + ((noTimeout) ? "disabled." : "enabled."));
                            break;
                        case KeyEvent.VK_MINUS:
                            newTransferVersion = (newTransferVersion > 0) ? newTransferVersion - 1 : newTransferVersion;
                            break;
                        case KeyEvent.VK_R:
                            rotate = !rotate;
                            break;
                        case KeyEvent.VK_N:
                            noise = true;//!noise;
                            break;
                        case KeyEvent.VK_PLUS:
                        case KeyEvent.VK_EQUALS:
                            newTransferVersion = (newTransferVersion < 39) ? newTransferVersion + 1 : newTransferVersion;
                            break;
                        case KeyEvent.VK_L:
                            JFrame window = new JFrame();
                            window.setTitle(frame.getTitle() + " full log");
                            window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                            JTextArea textArea = new JTextArea();
                            StringBuilder sb = new StringBuilder();
                            for (String s : new ArrayList<>(status)) {
                                sb.append(s).append("\n");
                            }
                            textArea.setText(sb.toString());
                            window.add(new JScrollPane(textArea));
                            window.pack();
                            window.setVisible(true);
                            break;
                    }
                } else if (e.getID() == KeyEvent.KEY_TYPED) {
                }
                return false;
            }
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);

        if (fullscreen) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
        } else {
            frame.setPreferredSize(new Dimension(800, 400));
        }
        frame.pack();
        frame.setVisible(true);
    }

    private void logOver(String newStatus) {
        System.out.println(newStatus);
        status.set(status.size() - 1, newStatus);
        //status = status.substring(0, status.lastIndexOf("\n") + 1) + newStatus;
    }

    private void log(String newStatus) {
        System.out.println(newStatus);
        status.add(newStatus);
//        if (lineCount > 16) {
//            status = status.substring(status.indexOf("\n") + 1, status.length());
//        }
//        status += newStatus + "\n";
//        lineCount++;
    }

    static public final double map(double value, double istart, double istop, double ostart, double ostop) {
        return ostart + (ostop - ostart) * ((value - istart) / (istop - istart));
    }

    public void readQRCode(BufferedImage img) {

        readerTotalCounter++;
        readerTempCounter = (readerTempCounter == 30) ? 1 : readerTempCounter + 1;

        try {
            if (interference || imageNotAvailable) {
                throw new Exception();
            }
            // Now test to see if it has a QR code embedded in it
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Hashtable<DecodeHintType, Object> hints = new Hashtable<>();

            if (harder || true) {
                hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            }

            result = reader.decode(bitmap, hints);

            Vector byteSegments = (Vector) result.getResultMetadata().get(ResultMetadataType.BYTE_SEGMENTS);

            int i = 0;
            int tam = 0;
            for (Object o : byteSegments) {
                byte[] bs = (byte[]) o;
                tam += bs.length;
            }

            dataRecived = new byte[tam];
            i = 0;
            for (Object o : byteSegments) {
                byte[] bs = (byte[]) o;
                for (byte b : bs) {
                    dataRecived[i++] = b;
                }
            }

            if (!Arrays.equals(lastDataRecived, dataRecived)) {
                lastDataRecived = dataRecived;
//                log("<(" + dataRecived.length + " bytes) " + Arrays.toString(dataRecived));
                secondaryCounter++;
                readOk = true;
            } else {
                readOk = false;
            }

            readerSucessCounter++;

            error = 0;

        } catch (Exception n) {
            readOk = false;
            result = null;
            error++;
            if (maxError > 0 && error >= maxError) {
                secondaryCounter++;
                error = 0;
            }
        }

        if (readerTempCounter == 1) {
            readerSucessCounter = 0;
            readerTotalCounter = 0;
        }

        conQuality = (((float) readerSucessCounter / readerTotalCounter) * 100);
    }

    public BufferedImage makeQRCode(byte[] data) {

        try {
            Hashtable<EncodeHintType, ErrorCorrectionLevel> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, (errorCorrectionLevel == 0) ? ErrorCorrectionLevel.L
                    : (errorCorrectionLevel == 1) ? ErrorCorrectionLevel.M
                            : (errorCorrectionLevel == 2) ? ErrorCorrectionLevel.Q
                                    : (errorCorrectionLevel == 3) ? ErrorCorrectionLevel.H
                                            : null);
            BitMatrix matrix = new QRCodeWriter().encode(new String(data, Charset.forName("ISO-8859-1")),
                    com.google.zxing.BarcodeFormat.QR_CODE, QRSize, QRSize, hints);
            dataSendLength = data.length;
            qrCode = MatrixToImageWriter.toBufferedImage(matrix);
            return qrCode;
        } catch (WriterException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected ImageProvider createImageProvider() {
        new Thread("Image Delay") {
            int i = 0;

            @Override
            public void run() {
                while (!terminated) {
                    imageNotAvailable = !imageNotAvailable;
//                    try {
//                        Thread.sleep(100);
//                    } catch (Exception e) {
//
//                    }
                }
            }
        }.start();
        return new ImageProvider() {

            @Override
            public BufferedImage getImage() {
                return shake(qrCode);
            }

            @Override
            public Object getObject() {
                return EyeFi.this;
            }
        };
    }

    public BufferedImage shake(BufferedImage inImg) {
        if (inImg == null) {
            return null;
        }
        BufferedImage outImg = new BufferedImage(inImg.getWidth(), inImg.getHeight(), BufferedImage.TYPE_INT_RGB);
        if (noise) {
            NoiseFilter noiseFilter = new NoiseFilter();
            noiseFilter.setMonochrome(true);
            noiseFilter.setDensity(.005f);
            noiseFilter.setAmount(2000);
            BufferedImage bufferedImage = new BufferedImage(inImg.getWidth(), inImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            noiseFilter.filter(inImg, bufferedImage);
            inImg = bufferedImage;
        }
        int w = inImg.getWidth();
        int h = inImg.getHeight();
        Graphics2D g2 = outImg.createGraphics();
        g2.setColor(Color.black);
        g2.fillRect(0, 0, w, h);
        g2.translate(inImg.getWidth() / 2, h / 2);
        g2.rotate(rotation);
        g2.drawImage(inImg, -w / 2, -h / 2, w, h, Color.BLACK, null);
        g2.dispose();

        return outImg;
    }

    public static float incrementalAverageIteration(float previousAverage, float value, int iterationIndex) {
        return ((value - previousAverage) / (iterationIndex + 1)) + previousAverage;
    }

    private static byte[] processChecksum(File file) {

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] dataBytes = new byte[1024];
                int nread;
                while ((nread = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, nread);
                }
                byte[] digest = md.digest();
                return digest;
            } catch (FileNotFoundException ex) {
            } catch (IOException ex) {
            }
        } catch (NoSuchAlgorithmException ex) {
        }
        return null;
    }

    public String getHexString(byte[] hash) {
        BigInteger bigInt = new BigInteger(1, hash);
        return bigInt.toString(16);
    }

}
