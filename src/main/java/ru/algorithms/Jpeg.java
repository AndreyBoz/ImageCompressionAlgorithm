package ru.algorithms;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;

public class Jpeg {
    private byte[][] Y, Cb,Cr;
    private int width, height;
    private int compressionRatio;
    private int blocks_encoded = 0;
    private static int[][][] quantizationTables = {{
            { 53, 37, 31, 53, 86, 139, 175, 211 },
            { 42, 40, 48, 66, 91, 199, 205, 188 },
            { 49, 45, 56, 84, 139, 199, 243, 200 },
            { 49, 60, 77, 103, 175, 299, 276, 212 },
            { 63, 77, 130, 195, 237, 382, 362, 272 },
            { 84, 123, 195, 226, 285, 367, 402, 327 },
            { 172, 224, 272, 303, 362, 426, 422, 359 },
            { 255, 327, 339, 350, 398, 354, 366, 353 }
    },
            {{16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}
    },
            {{ 1, 1, 1, 1, 1, 2, 3, 4 },
            { 1, 1, 1, 1, 2, 2, 3, 4 },
            { 1, 1, 1, 2, 3, 3, 3, 4 },
            { 1, 1, 2, 3, 4, 4, 4, 4 },
            { 1, 2, 3, 4, 5, 5, 5, 4 },
            { 2, 2, 3, 4, 5, 6, 6, 5 },
            { 3, 3, 3, 4, 5, 6, 7, 6 },
            { 4, 4, 4, 4, 4, 5, 6, 7 }}
    };
    public Jpeg(String inputDir, int compressionRatio) {
        try {
            this.compressionRatio = compressionRatio;
            BufferedImage input = ImageIO.read(new File(inputDir));
            this.width = input.getWidth();
            this.height = input.getHeight();
            getYCbCr(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void encodeToNewJpeg(String output){
        try(DataOutputStream out = new DataOutputStream(new FileOutputStream(output+".newjpeg"))) {
            byte[][][] colors = new byte[3][][];
            colors[0] = this.Y;
            colors[1] = this.Cb;
            colors[2] = this.Cr;
            out.write((byte)this.compressionRatio);

            for (int i = 0; i < colors.length; i++) {
                for (int j = 0; j < colors[i].length - 7; j += 8) {
                    for (int l = 0; l < colors[i][0].length - 7; l += 8) {
                        int[][] block = getBlock(colors[i], j, l, 8);
                        block = DCT(block);
                        block = quantization(block, this.compressionRatio);
                        int[] dataBlock = performZigzag(block);
                        byte[] result = runLengthEncode(dataBlock);
                        out.write(result);
                        blocks_encoded++;
                    }
                }
            }
        }catch (IOException ioException){
            System.out.println(ioException);
        }
    }
    public void decodeToImg(String inputFile,String outputFile) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(inputFile))) {
            byte compressionRatio = in.readByte();

            byte[][][] colors = new byte[3][][];
            colors[0] = new byte[this.Y.length][this.Y[0].length];
            colors[1] = new byte[this.Cb.length][this.Cb[0].length];
            colors[2] = new byte[this.Cr.length][this.Cr[0].length];

            for (int i = 0; i < colors.length; i++) {
                for (int j = 0; j < colors[i].length - 7; j += 8) {
                    for (int l = 0; l < colors[i][0].length - 7; l += 8) {
                        byte[] encodedData = new byte[128];
                        in.read(encodedData);

                        int[] dataBlock = runLengthDecode(encodedData);
                        int[][] block = performInverseZigzag(dataBlock);
                        block = dequantization(block, compressionRatio);
                        block = inverseDCT(block);
                        setBlock(block, colors[i], j, l, 8);
                    }
                }
            }

            // Создание нового изображения и сохранение его
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int YVal = colors[0][x][y] & 0xFF;
                    int CbVal = colors[1][x / 2][y / 2] & 0xFF; // получаем младшие биты от 0 до 255
                    int CrVal = colors[2][x / 2][y / 2] & 0xFF;

                    // Преобразование YCbCr в RGB
                    int R = (int) (YVal + 1.402 * (CrVal - 128));
                    int G = (int) (YVal - 0.344136 * (CbVal - 128) - 0.714136 * (CrVal - 128));
                    int B = (int) (YVal + 1.772 * (CbVal - 128));

                    // Ограничение значений в диапазоне 0-255
                    R = Math.min(255, Math.max(0, R));
                    G = Math.min(255, Math.max(0, G));
                    B = Math.min(255, Math.max(0, B));

                    // Создание цвета RGB и установка пикселя в новом изображении
                    Color color = new Color(R, G, B);
                    newImage.setRGB(x, y, color.getRGB());
                }
            }
            ImageIO.write(newImage, "jpg", new File(outputFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private int[][] getBlock(byte[][] colors, int x, int y, int blockSize) {
        int[][] block = new int[blockSize][blockSize];

        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                block[i][j] = colors[x + i][y + j] & 0xFF; // Преобразование значения в беззнаковый байт
            }
        }

        return block;
    }
    private void setBlock(int[][] block, byte[][] colors, int x, int y, int blockSize) {
        for (int i = 0; i < blockSize; i++) {
            for (int j = 0; j < blockSize; j++) {
                colors[x + i][y + j] = (byte) block[i][j];
            }
        }
    }
    private void getYCbCr(BufferedImage input) {
        Y = new byte[width][height];
        Cb = new byte[width/2][height/2];
        Cr = new byte[width/2][height/2];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color color = new Color(input.getRGB(x, y));
                int R = color.getRed();
                int G = color.getGreen();
                int B = color.getBlue();

                // Преобразование RGB в YCbCr
                double YVal = 0.299 * R + 0.587 * G + 0.114 * B;
                double CbVal = -0.1687 * R - 0.3313 * G + 0.5 * B + 128;
                double CrVal = 0.5 * R - 0.4187 * G - 0.0813 * B + 128;

                // Ограничение значений в диапазоне 0-255
                Y[x][y] = (byte) Math.min(255, Math.max(0, YVal));
                Cb[x / 2][y / 2] = (byte) Math.min(255, Math.max(0, CbVal));
                Cr[x / 2][y / 2] = (byte) Math.min(255, Math.max(0, CrVal));
            }
        }
    }
    public static double C(int i, int u) {
        return  (u == 0 ? 1.0 / Math.sqrt(2) : 1.0) * Math.cos(((2 * i + 1) * u * Math.PI) / 16.0);
    }
    private int[][] DCT(int[][] input) {
        int[][] result = new int[8][8];

        for(int u = 0; u < 8; u++)
        {
            for(int v = 0; v < 8; v++)
            {
                double s = 0.0;
                for(int x = 0; x < 8; x++)
                {
                    for(int y = 0; y < 8; y++)
                    {
                        s += input[x][y] * C(x, u) * C(y, v);
                    }
                }
                s *= 0.25;
                result[u][v] = (int)(s);
            }
        }
        return result;
    }
    public static int[][] quantization(int[][] block, int quality) {
        int[][] table = quantizationTables[quality];
        int[][] result = new int[8][8];
        for (int i = 0; i < 8; i++)
        {
            for(int j = 0; j < 8; j++)
            {
                result[i][j] = block[i][j] / table[i][j];
            }
        }
        return result;
    }
    private int[] performZigzag(int[][] block) {
        int[] result = new int[64];
        int index = 0;
        int i = 0;
        int j = 0;
        boolean direction = false;
        while (index < 64) {
            result[index] = block[i][j];
            index++;

            if (direction) {
                if (j == 7) {
                    i++;
                    direction = !direction;
                } else if (i == 0) {
                    j++;
                    direction = !direction;
                } else {
                    i--;
                    j++;
                }
            } else {
                if (i == 7) {
                    j++;
                    direction = !direction;
                } else if (j == 0) {
                    i++;
                    direction = !direction;
                } else {
                    i++;
                    j--;
                }
            }
        }

        return result;
    }

    private int[][] zigzagFill(ArrayList<Short> block) {
        int[][] result = new int[8][8];
        int index = 0;
        int i = 0;
        int j = 0;
        boolean direction = false; // false = down-right, true = up-left

        while (index < 64) {
            result[i][j] = block.get(index);
            index++;

            if (direction) {
                // Up-left direction
                if (j == 7) {
                    i++;
                    direction = !direction;
                } else if (i == 0) {
                    j++;
                    direction = !direction;
                } else {
                    i--;
                    j++;
                }
            } else {
                // Down-right direction
                if (i == 7) {
                    j++;
                    direction = !direction;
                } else if (j == 0) {
                    i++;
                    direction = !direction;
                } else {
                    i++;
                    j--;
                }
            }
        }

        return result;
    }
    private byte[] runLengthEncode(int[] data) {
        ArrayList<Byte> encodedList = new ArrayList<>();
        int count = 1;

        for (int i = 1; i < data.length; i++) {
            if (data[i] == data[i - 1]) {
                count++;
            } else {
                encodedList.add((byte) count);
                encodedList.add((byte) data[i - 1]);
                count = 1;
            }
        }

        // Add the count and last value
        encodedList.add((byte) count);
        encodedList.add((byte) data[data.length - 1]);

        // Convert List<Byte> to byte[]
        byte[] encodedData = new byte[encodedList.size()];
        for (int i = 0; i < encodedList.size(); i++) {
            encodedData[i] = encodedList.get(i);
        }

        return encodedData;
    }
    private int[] runLengthDecode(byte[] encodedData) {
        ArrayList<Integer> decodedData = new ArrayList<>();

        int index = 0;
        while (index < encodedData.length) {
            byte value = encodedData[index];
            index++;

            if (index < encodedData.length) { // Проверка границ массива
                byte counter = encodedData[index];
                index++;

                for (int i = 0; i < counter; i++) {
                    decodedData.add(0);
                }
            } else {
                decodedData.add((int) value);
            }
        }

        int[] result = new int[decodedData.size()];
        for (int i = 0; i < decodedData.size(); i++) {
            result[i] = decodedData.get(i);
        }

        return result;
    }

    private int[][] performInverseZigzag(int[] dataBlock) {
        int[][] block = new int[8][8];
        int index = 0;

        for (int sum = 0; sum <= 14; sum++) {
            for (int i = Math.min(sum, 7); i >= Math.max(0, sum - 7); i--) {
                int j = sum - i;
                if (index < dataBlock.length) { // Проверка границ массива
                    if (sum % 2 == 0) {
                        block[i][j] = dataBlock[index];
                    } else {
                        block[j][i] = dataBlock[index];
                    }
                    index++;
                }
            }
        }

        return block;
    }

    private int[][] dequantization(int[][] block, byte compressionRatio) {
        int[][] dequantizedBlock = new int[8][8];
        int[][] quantizationMatrix = quantizationTables[compressionRatio];

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                dequantizedBlock[i][j] = block[i][j] * quantizationMatrix[i][j];
            }
        }

        return dequantizedBlock;
    }

    private int[][] inverseDCT(int[][] input) {
        int[][] result = new int[8][8];

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                double s = 0.0;
                for (int u = 0; u < 8; u++) {
                    for (int v = 0; v < 8; v++) {
                        s += input[u][v] * C(u, x) * C(v, y);
                    }
                }
                s *= 0.25;
                result[x][y] = (int) (s);
            }
        }

        return result;
    }


}
