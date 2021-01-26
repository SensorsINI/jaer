package net.sf.jaer.util.avioutput;

import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import java.awt.image.*;
import java.io.*;
import java.util.Iterator;

/**
 * This class will generate an animated GIF from a sequence of individual
 * images. Embedded in Wayang to facilitate capturing animations and interfaces
 * rendered on the Push, for producing great online documentation.
 *
 * Originally created by Elliot Kroo on 2009-04-25. See
 * http://elliot.kroo.net/software/java/GifSequenceWriter/ James Elliott split
 * the constructor into a variety of different versions, to accommodate the
 * needs of Wayang.
 *
 * This work is licensed under the Creative Commons Attribution 3.0 Unported
 * License. To view a copy of this license, visit
 * http://creativecommons.org/licenses/by/3.0/ or send a letter to Creative
 * Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.
 *
 * @author Elliot Kroo (elliot[at]kroo[dot]net)
 * @see https://github.com/Deep-Symmetry/wayang#wayang
 */
public class GifSequenceWriter implements VideoFrameWriterInterface {

    protected ImageWriter gifWriter;
    protected ImageWriteParam imageWriteParam;
    protected IIOMetadata imageMetaData;

    /**
     * Creates a new GifSequenceWriter from an existing buffered image.
     *
     * @param outputStream the ImageOutputStream to be written to
     * @param image the source image that will be written to the output
     * @param timeBetweenFramesMS the time between frames in milliseconds
     * @param loopContinuously whether the gif should loop repeatedly
     * @throws IIOException if no gif ImageWriters are found
     *
     * @author James Elliott
     */
    public GifSequenceWriter(
            ImageOutputStream outputStream,
            RenderedImage image,
            int timeBetweenFramesMS,
            boolean loopContinuously) throws IIOException, IOException {
        this(outputStream, ImageTypeSpecifier.createFromRenderedImage(image), timeBetweenFramesMS,
                loopContinuously);
    }

    /**
     * Creates a new GifSequenceWriter
     *
     * @param outputStream the ImageOutputStream to be written to
     * @param imageType one of the imageTypes specified in BufferedImage
     * @param timeBetweenFramesMS the time between frames in miliseconds
     * @param loopContinuously wether the gif should loop repeatedly
     * @throws IIOException if no gif ImageWriters are found
     *
     * @author Elliot Kroo (elliot[at]kroo[dot]net)
     */
    public GifSequenceWriter(
            ImageOutputStream outputStream,
            int imageType,
            int timeBetweenFramesMS,
            boolean loopContinuously) throws IIOException, IOException {
        this(outputStream, ImageTypeSpecifier.createFromBufferedImageType(imageType), timeBetweenFramesMS,
                loopContinuously);

    }

    /**
     * Creates a new GifSequenceWriter
     *
     * @param outputStream the ImageOutputStream to be written to
     * @param imageTypeSpecifier the type of images to be written
     * @param timeBetweenFramesMS the time between frames in miliseconds
     * @param loopContinuously wether the gif should loop repeatedly
     * @throws IIOException if no gif ImageWriters are found
     *
     * @author Elliot Kroo (elliot[at]kroo[dot]net)
     */
    public GifSequenceWriter(
            ImageOutputStream outputStream,
            ImageTypeSpecifier imageTypeSpecifier,
            int timeBetweenFramesMS,
            boolean loopContinuously) throws IIOException, IOException {

        // my method to create a writer
        gifWriter = getWriter();
        imageWriteParam = gifWriter.getDefaultWriteParam();

        imageMetaData
                = gifWriter.getDefaultImageMetadata(imageTypeSpecifier,
                        imageWriteParam);

        String metaFormatName = imageMetaData.getNativeMetadataFormatName();

        IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);

        IIOMetadataNode graphicsControlExtensionNode = getNode(
                root,
                "GraphicControlExtension");

        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute(
                "transparentColorFlag",
                "FALSE");
        graphicsControlExtensionNode.setAttribute(
                "delayTime",
                Integer.toString(timeBetweenFramesMS / 10));
        graphicsControlExtensionNode.setAttribute(
                "transparentColorIndex",
                "0");

        IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
        commentsNode.setAttribute("CommentExtension", "Created by MAH");

        IIOMetadataNode appEntensionsNode = getNode(
                root,
                "ApplicationExtensions");

        IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");

        child.setAttribute("applicationID", "NETSCAPE");
        child.setAttribute("authenticationCode", "2.0");

        int loop = loopContinuously ? 0 : 1;

        child.setUserObject(new byte[]{0x1, (byte) (loop & 0xFF), (byte) ((loop >> 8) & 0xFF)});
        appEntensionsNode.appendChild(child);

        imageMetaData.setFromTree(metaFormatName, root);

        gifWriter.setOutput(outputStream);

        gifWriter.prepareWriteSequence(null);
    }

    @Override
    public void writeFrame(BufferedImage img) throws IOException {
        gifWriter.writeToSequence(
                new IIOImage(
                        img,
                        null,
                        imageMetaData),
                imageWriteParam);
    }

    /**
     * Close this GifSequenceWriter object. This does not close the underlying
     * stream, just finishes off the GIF.
     *
     * @throws IOException if there is a problem writing the last bytes.
     */
    @Override
    public void close() throws IOException {
        gifWriter.endWriteSequence();
        if(gifWriter.getOutput() instanceof FileImageOutputStream){
            FileImageOutputStream os=(FileImageOutputStream)gifWriter.getOutput();
            os.flush();
            os.close();
        }
    }

    /**
     * Returns the first available GIF ImageWriter using
     * ImageIO.getImageWritersBySuffix("gif").
     *
     * @return a GIF ImageWriter object
     * @throws IIOException if no GIF image writers are returned
     */
    private static ImageWriter getWriter() throws IIOException {
        Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix("gif");
        if (!iter.hasNext()) {
            throw new IIOException("No GIF Image Writers Exist");
        } else {
            return iter.next();
        }
    }

    /**
     * Returns an existing child node, or creates and returns a new child node
     * (if the requested node does not exist).
     *
     * @param rootNode the <tt>IIOMetadataNode</tt> to search for the child
     * node.
     * @param nodeName the name of the child node.
     *
     * @return the child node, if found or a new node created with the given
     * name.
     */
    private static IIOMetadataNode getNode(
            IIOMetadataNode rootNode,
            String nodeName) {
        int nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName)
                    == 0) {
                return ((IIOMetadataNode) rootNode.item(i));
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return (node);
    }

    /**
     * Support invocation from the command line; provide a list of input file
     * names, followed by a single output file name.
     *
     * @param args the names of the image files to be combined into a GIF
     * sequence, folloewd by the output file name.
     *
     * @throws Exception if there is a problem reading the inputs or writing the
     * output.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            // grab the output image type from the first image in the sequence
            BufferedImage firstImage = ImageIO.read(new File(args[0]));

            // create a new BufferedOutputStream with the last argument
            ImageOutputStream output
                    = new FileImageOutputStream(new File(args[args.length - 1]));

            // create a gif sequence with the type of the first image, 1 second
            // between frames, which loops continuously
            GifSequenceWriter writer
                    = new GifSequenceWriter(output, firstImage.getType(), 1, false);

            // write out the first image to our sequence...
            writer.writeFrame(firstImage);
            for (int i = 1; i < args.length - 1; i++) {
                BufferedImage nextImage = ImageIO.read(new File(args[i]));
                writer.writeFrame(nextImage);
            }

            writer.close();
            output.close();
        } else {
            System.out.println(
                    "Usage: java GifSequenceWriter [list of gif files] [output file]");
        }
    }
}
