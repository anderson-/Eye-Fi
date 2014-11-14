/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eyefi;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.FormatException;
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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author gnome3
 */
interface ImageProvider {

    public BufferedImage getImage();

    public Object getObject();
}

public class EyeFi implements ImageProvider {

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
    public static final byte CMD_LIST = 21;
    public static final byte CMD_PING = 40;
    public static final byte CMD_PONG = 50;
    public static final byte CMD_REQUEST_END = 60;
    public static final byte CMD_END = 70;

    Webcam webcam = null;
    private Dimension videoSize = WebcamResolution.VGA.getSize();

    int width = 1;
    int height = 1;

    int secondaryCounter = 0;

    int errorCorrectionLevel = 0;
    int QRSize = 200;
    int dataSendLength = 0;

    String status = "";
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
    private BufferedImage qrCode;
    private ImageProvider imageProvider;
    private JFrame frame;
    final AffineTransform rotation = AffineTransform.getTranslateInstance(QRSize / 2, QRSize / 2);
    int nMessages;
    int maxVersion;
    int minErrorCorrectionLevel;
    int maxErrorCorrectionLevel;
    int timeout;

    public EyeFi(int nMessages, int maxVersion, int minErrorCorrectionLevel, int maxErrorCorrectionLevel, int timeout) {
        this.nMessages = nMessages;
        this.maxVersion = maxVersion;
        this.minErrorCorrectionLevel = minErrorCorrectionLevel;
        this.maxErrorCorrectionLevel = maxErrorCorrectionLevel;
        this.timeout = timeout;
    }

    public void run() {
        createWindow();
    }

    public void initWebcam() {
        new Thread() {
            @Override
            public void run() {
                Webcam w = Webcam.getDefault();
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
                        while (true) {
                            if (webcam != null) {
                                img = webcam.getImage();
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

    public static void main(String[] args) {

        boolean simulate = false;
        String filePath = "robof64.png";
        boolean fullscreen = true;
        int maxMessages = 5;
        int maxVersion = QR_CAP.length - 1;
        int minECL = 0;
        int maxECL = QR_CAP[0].length - 1;
        int timeout = 30000;

        for (Iterator<String> it = Arrays.asList(args).iterator(); it.hasNext();) {
            String arg = it.next();
            switch (arg) {
                case "-h":
                case "--help":
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
                    break;
                case "-e":
                case "--minECL":
                    minECL = Integer.parseInt(it.next());
                    break;
                case "-E":
                case "--maxECL":
                    maxECL = Integer.parseInt(it.next());
                    break;
                case "-m":
                case "--maxMessages":
                    maxMessages = Integer.parseInt(it.next());
                    break;
                case "-s":
                case "--simulate":
                    simulate = true;
                    break;
                case "-r":
                case "--receiveMode":
                    filePath = null;
                    break;
                case "-t":
                case "--timeout":
                    timeout = Integer.parseInt(it.next());
                    break;
                default:
                    System.out.println("Invalid Argument");
                    System.exit(0);
            }
        }

        if (!simulate) {
            EyeFi qrcomm = new EyeFi(maxMessages, maxVersion, minECL, maxECL, timeout);
            qrcomm.initWebcam();
            qrcomm.setFullScreen(fullscreen);
            qrcomm.run();
            if (filePath != null && !filePath.isEmpty()) {
                qrcomm.serverMode(filePath);
            } else {
                qrcomm.receiveMode();
            }
        } else {
            final String path = filePath;
            final EyeFi a = new EyeFi(maxMessages, maxVersion, minECL, maxECL, timeout);
            final EyeFi b = new EyeFi(maxMessages, maxVersion, minECL, maxECL, timeout);
            a.setFullScreen(false);
            b.setFullScreen(false);
            a.setImageProvider(b);
            b.setImageProvider(a);
            a.run();
            b.run();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
            new Thread("Client") {
                @Override
                public void run() {
                    a.receiveMode();
                }
            }.start();
            new Thread("Server") {
                @Override
                public void run() {
                    b.serverMode(path);
                }
            }.start();
        }
    }

    public void send(byte... data) {
        makeQRCode(data);
    }

    public byte[] read() {
        long t = System.currentTimeMillis();
        while (System.currentTimeMillis() - t < timeout) {
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

    public void serverMode(String filePath) {
        frame.setTitle("Server");
        try {
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

            log("Loaded: \"" + fileName + "\"[" + fileNameLength + "], providing " + dataLen + " Bytes.");

            while (true) {
                read();
                if (dataRecived != null) {
                    switch (dataRecived[0]) {
                        case CMD_PING:
                            log("Responding to ping(" + dataRecived[1] + "," + dataRecived[2] + "), " + QR_CAP[dataRecived[1]][dataRecived[2]] + " bytes sent");
                            send(genByteArray(QR_CAP[dataRecived[1]][dataRecived[2]], true, CMD_PONG, dataRecived[1], dataRecived[2]));
                            break;
                        case CMD_REQUEST_LIST:
                            log("sending distribution");
                            send(CMD_LIST, (byte) fileNameLength, (byte) (dataLen >>> 24), (byte) (dataLen >>> 16), (byte) (dataLen >>> 8), (byte) dataLen);
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
                            log("sending section " + index);
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
                            }
                            break;
                        case CMD_REQUEST_END:
                            send(genByteArray(7, true, CMD_END));
                            log("THE END");
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

    public void receiveMode() {
        frame.setTitle("Client");
        int[] parameters = getOptimalTransferParameters(nMessages, maxVersion, minErrorCorrectionLevel, maxErrorCorrectionLevel);
        int[] dataPos = getDataDistribution();
        String name = readString(0, dataPos[0], parameters[0], parameters[1]);
        log("Loading " + name);
        long t = System.currentTimeMillis();
        File file = receiveFile(name, dataPos[0], dataPos[1] - dataPos[0], parameters[0], parameters[1]);
        float seconds = ((System.currentTimeMillis() - t) / 1000f);
        float kbps = (file.length() / 1000f) / seconds;
        terminate();
        file = copyFile(file, new File("new" + name));
        showPictureFrame(file, name + " : transfered in " + seconds + " s (" + kbps + " kB/s)");
    }

    public void showPictureFrame(File file, String title) {
        try {
            JFrame picFrame = new JFrame(title);
            picFrame.add(new JLabel(new ImageIcon(ImageIO.read(file))));
            picFrame.pack();
//            if (imgFrame.getWidth() < 300) {
//                if (imgFrame.getHeight() < 300) {
//                    imgFrame.setSize(new Dimension(300, 300));
//                } else {
//                    imgFrame.setSize(new Dimension(300, imgFrame.getHeight()));
//                }
//            }
            picFrame.setSize(new Dimension(picFrame.getWidth() + 200, picFrame.getHeight() + 200));
            picFrame.setLocationRelativeTo(null);
            picFrame.setVisible(true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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
            log("data: " + dataDis[0] + " " + dataDis[1]);
            return dataDis;
        }
        return null;
    }

    public byte[] readSection(int start, int size, int version, int errorCorrection) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.clear(); //prepare buffer to fill with data.
        int counterByteSize = (int) Math.floor((31 - Integer.numberOfLeadingZeros(size)) / 8f) + 1;
        int qrCodeCapacity = QR_CAP[version][errorCorrection] - (1 + counterByteSize);
        for (int i = start; i < start + size; i += qrCodeCapacity) {
            send(CMD_REQUEST_SECTION, (byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i, (byte) version, (byte) errorCorrection, (byte) counterByteSize);
            read();
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
                        length = size % qrCodeCapacity;
                    }
                    buffer.put(dataRecived, offset, length);

                }
            }
        }
        buffer.flip();
        return buffer.array();
    }

    public String readString(int start, int size, int version, int errorCorrection) {
        return new String(readSection(start, size, version, errorCorrection));
    }

    public File receiveFile(String name, int start, int size, int version, int errorCorrection) {
        try {
            byte[] data = readSection(start, size, version, errorCorrection);
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

    public float ping(int version, int errorCorrection) {
        send(genByteArray(7, true, CMD_PING, (byte) version, (byte) errorCorrection));
        long t = System.currentTimeMillis();
        read();
        if (dataRecived != null && dataRecived[0] == CMD_PONG
                && dataRecived[1] == version
                && dataRecived[2] == errorCorrection) {
            long ping = System.currentTimeMillis() - t;
            int capacity = QR_CAP[version][errorCorrection];
            return capacity * (1000f / ping);
        }
        return 0;
    }

    public float benchmark(int version, int errorCorrection, int nMessages) {
        float averagePing = 0;
        for (int i = 0; i < nMessages; i++) {
            averagePing = incrementalAverageIteration(averagePing, ping(version, errorCorrection), i);
        }
        return averagePing;
    }

    public int[] getOptimalTransferParameters(int nMessages, int maxVersion, int minErrorCorrectionLevel, int maxErrorCorrectionLevel) {
        int[] ret = new int[]{0, 0};
        float max = 0;
        try {
            for (int v = 0; v <= maxVersion; v++) {
                for (int ec = minErrorCorrectionLevel; ec <= maxErrorCorrectionLevel; ec++) {
                    float res = benchmark(v, ec, nMessages);
                    log("ping(" + v + "," + ec + "): " + (int) res + " B/s");
                    if (res > max) {
                        max = res;
                        ret[0] = v;
                        ret[1] = ec;
                    }
                }
            }
        } catch (TimeoutException e) {

        }
        log("Best: ping(" + ret[0] + "," + ret[1] + ") at " + ((max > 1000) ? (int) (max / 1000) + " kB/s" : max + " B/s"));
        ret[0]--;
        return ret;
    }

    public void terminate() {
        send(genByteArray(7, true, CMD_REQUEST_END));
        read();
        if (dataRecived != null && dataRecived[0] == CMD_END) {
            log("THE END");
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
        if (imageProvider instanceof EyeFi) {
            //Rotate image to provide diversity to the reader.
            //It reduces the throwing of ChecksumException and FormatException
            //for some reason that I dont know why...
            new Thread("Rotate") {
                int i = 0;

                @Override
                public void run() {
                    while (true) {
                        rotation.rotate(Math.PI / 2);
                        i++;
                        switch (i % 7) {
                            case 0:
                                rotation.rotate(.04);
                                break;
                            case 2:
                                rotation.rotate(.02);
                                break;
                            case 4:
                                rotation.rotate(-.02);
                                break;
                            case 6:
                                rotation.rotate(-.04);
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
    }

    private void setFullScreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public final void createWindow() {
        frame = new JFrame();

        final JPanel c = new JPanel() {
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

                g2.setColor(Color.black);
                g2.drawString("Error Correction Level : "
                        + ((errorCorrectionLevel == 0) ? "L"
                                : (errorCorrectionLevel == 1) ? "M"
                                        : (errorCorrectionLevel == 2) ? "Q"
                                                : (errorCorrectionLevel == 3) ? "H"
                                                        : "err")
                        + " | Data Length (bytes) : " + dataSendLength, 10, 25);

                g2.setColor(Color.white);
                g2.drawLine(height + 1, 0, height + 1, height);
                g2.drawLine(height + 3, 0, height + 3, height);

                {
                    int y = 20;
                    for (String line : status.split("\n")) {
                        g2.drawString(line, height + 20, y += g.getFontMetrics().getHeight());
                    }
                }

                if (imageProvider != null && imageProvider.getImage() != null) {
                    g2.setColor(Color.white);
                    if (imageProvider.getObject() instanceof Webcam) {
                        Webcam webcam = (Webcam) imageProvider.getObject();
                        g2.drawString("fps : " + (int) webcam.getFPS(), width - 140, height - 125);
                    }
                    g2.drawImage(imageProvider.getImage(), width - 160, height - 120, 160, 120, Color.black, null);
                    if (result != null) {
                        if (harder) {
                            g2.setColor(Color.red);
                        } else {
                            g2.setColor(Color.green);
                        }
                        for (ResultPoint p : result.getResultPoints()) {
                            g2.fillOval((int) map(p.getX(), 0, videoSize.width, 0, 160) + (width - 160), (int) map(p.getY(), 0, videoSize.height, 0, 120) + (height - 120), 7, 7);
                        }
                    }
                } else {
                    g2.setColor(Color.darkGray);
                    int x = width - 160;
                    int y = height - 120;
                    int w = 160;
                    int h = 120;
                    g2.drawRect(x, y, w, h);
                    g2.drawLine(x, y, x + w, y + h);
                    g2.drawLine(x, y + h, x + w, y);
                    g2.setColor(Color.white);
                    g2.drawString("Waiting...", x + w / 2 - 30, y + h / 2 + 5);
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
                while (true) {
                    c.repaint();
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
                    }
                } else if (e.getID() == KeyEvent.KEY_TYPED) {
                }
                return false;
            }
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(c);

        if (fullscreen) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
        } else {
            frame.setPreferredSize(new Dimension(800, 400));
        }
        frame.pack();
        frame.setVisible(true);
    }

    private void log(String newStatus) {
        if (lineCount > 16) {
            status = status.substring(status.indexOf("\n") + 1, status.length());
        }
        status += newStatus + "\n";
        lineCount++;
    }

    static public final double map(double value, double istart, double istop, double ostart, double ostop) {
        return ostart + (ostop - ostart) * ((value - istart) / (istop - istart));
    }

    public void readQRCode(BufferedImage img) {

        readerTotalCounter++;
        readerTempCounter = (readerTempCounter == 30) ? 1 : readerTempCounter + 1;

        try {

            // Now test to see if it has a QR code embedded in it
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Hashtable<DecodeHintType, Object> hints = new Hashtable<>();

            if (harder) {
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

        } catch (com.google.zxing.NotFoundException n) {

            error++;

            readOk = false;

            if (maxError > 0 && error >= maxError) {
                secondaryCounter++;
                error = 0;
            }

        } catch (ChecksumException | FormatException e) {
            readOk = false;
            error++;
            e.printStackTrace();
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

    @Override
    public BufferedImage getImage() {
        BufferedImage img = new BufferedImage(QRSize, QRSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, QRSize, QRSize);
        //rotation.rotate(.01);
        g2.setTransform(rotation);
        g2.drawImage(qrCode, -QRSize / 2, -QRSize / 2, QRSize, QRSize, Color.BLACK, null);
        g2.dispose();

        return img;
    }

    @Override
    public Object getObject() {
        return this;
    }

    public static float incrementalAverageIteration(float previousAverage, float value, int iterationIndex) {
        return ((value - previousAverage) / (iterationIndex + 1)) + previousAverage;
    }
}
