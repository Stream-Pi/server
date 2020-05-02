package in.dubbadhar.StreamPiServer;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;

public class dash extends dashBase {

    public dash()
    {
        try {
            setupConfig();
            initNodes();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        startServer();
    }

    public void setupConfig() throws Exception
    {
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class).configure(new Parameters().properties().setURL(getClass().getResource("config.properties").toURI().toURL()));
        builder.setAutoSave(true);
        config = builder.getConfiguration();
    }


    Thread serverThread;
    server s;
    public void startServer(){
        s = new server(config.getInt("server-port"), this);
        serverThread = new Thread(s, "Server Thread");
        serverThread.setDaemon(false);
        serverThread.start();
    }

    public void closeServer()
    {
        if(serverThread.isAlive())
        {
            s.close();
        }
    }
}
