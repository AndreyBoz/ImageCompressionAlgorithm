package ru.algorithms;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String inputFilePath = "input.bmp";
        Jpeg jpeg = new Jpeg(inputFilePath,0);
        jpeg.encodeToNewJpeg("output");
        jpeg.decodeToBMP("output.newjpeg","output.jpg");
    }
}
