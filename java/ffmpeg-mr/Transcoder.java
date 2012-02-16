import java.net.URL;
import java.util.zip.ZipFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Transcoder
{
    
    static {
        try {
            // get the class object for this class, and get the location of it
            final Class c = Transcoder.class;
            final URL location = c.getProtectionDomain().getCodeSource().getLocation();
            
            // jars are just zip files, get the input stream for the lib
            ZipFile zf = new ZipFile(location.getPath());
            InputStream in = zf.getInputStream(zf.getEntry("libffmpeg-mr.jnilib"));
            
            // create a temp file and an input stream for it
            File f = File.createTempFile("JARLIB-", "-libffmpeg-mr.jnilib");
            FileOutputStream out = new FileOutputStream(f);
            
            // copy the lib to the temp file
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0){
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
                
            // load the lib specified by it’s absolute path and delete it
            System.load(f.getAbsolutePath());
            f.delete();
                
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    
    private int input_codec = 0;
    private int output_codec = 0;
    
    public Transcoder(int input_codec, int output_codec)
    {
        this.input_codec = input_codec;
        this.output_codec = output_codec;
        verifyCodecs();
    }
    
    private native void verifyCodecs();
    
    
}