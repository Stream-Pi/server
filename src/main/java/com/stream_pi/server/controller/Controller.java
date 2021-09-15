package com.stream_pi.server.controller;

import com.stream_pi.action_api.action.Action;
import com.stream_pi.action_api.action.ActionType;
import com.stream_pi.action_api.action.PropertySaver;
import com.stream_pi.action_api.action.ServerConnection;
import com.stream_pi.action_api.externalplugin.NormalAction;
import com.stream_pi.action_api.externalplugin.ToggleAction;
import com.stream_pi.action_api.externalplugin.ToggleExtras;
import com.stream_pi.server.Main;
import com.stream_pi.server.action.ExternalPlugins;
import com.stream_pi.server.client.Client;
import com.stream_pi.server.client.ClientProfile;
import com.stream_pi.server.connection.ClientConnection;
import com.stream_pi.server.connection.ClientConnections;
import com.stream_pi.server.connection.MainServer;
import com.stream_pi.server.info.ServerInfo;
import com.stream_pi.server.info.StartupFlags;
import com.stream_pi.server.io.Config;
import com.stream_pi.server.window.Base;
import com.stream_pi.server.window.dashboard.DashboardBase;
import com.stream_pi.server.window.dashboard.DonatePopupContent;
import com.stream_pi.server.window.dashboard.actiongridpane.ActionBox;
import com.stream_pi.server.window.firsttimeuse.FirstTimeUse;
import com.stream_pi.server.window.settings.SettingsBase;
import com.stream_pi.util.alert.StreamPiAlert;
import com.stream_pi.util.alert.StreamPiAlertListener;
import com.stream_pi.util.alert.StreamPiAlertType;
import com.stream_pi.util.exception.*;
import com.stream_pi.util.iohelper.IOHelper;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.io.IOException;

import com.stream_pi.server.window.settings.GeneralSettings;

public class Controller extends Base implements PropertySaver, ServerConnection, ToggleExtras
{
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private MainServer mainServer;
    private Animation openSettingsAnimation;
    private Animation closeSettingsAnimation;
    public static boolean soundactivated;

    public Controller(){
        mainServer = null;
    }

    public void setupDashWindow() throws SevereException
    {
        try
        {
            getStage().setOnCloseRequest(this::onCloseRequest);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw new SevereException(e.getMessage());
        }
    }

    @Override
    public void init()
    {
        try
        {
            initBase();
            setupDashWindow();

            setupSettingsWindowsAnimations();

            ExternalPlugins.getInstance().setPropertySaver(this);
            ExternalPlugins.getInstance().setToggleExtras(this);

            ExternalPlugins.getInstance().setServerConnection(this);


            getDashboardBase().getPluginsPane().getSettingsButton().setOnAction(event -> {
                openSettingsAnimation.play();
            });

            getSettingsBase().getCloseButton().setOnAction(event -> {
                closeSettingsAnimation.play();
            });

            getSettingsBase().getThemesSettings().setController(this);

            mainServer = new MainServer(this, this);

            if(getConfig().isFirstTimeUse())
            {
                Stage stage = new Stage();
                Scene s = new Scene(new FirstTimeUse(this, this),
                        getConfig().getStartupWindowWidth(), getConfig().getStartupWindowHeight());
                stage.setScene(s);
                stage.setMinHeight(500);
                stage.setMinWidth(700);
                stage.getIcons().add(new Image(Objects.requireNonNull(Main.class.getResourceAsStream("app_icon.png"))));
                stage.setTitle("Stream-Pi Server Setup");
                stage.getScene().getStylesheets().addAll(getStylesheets());
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setOnCloseRequest(event->Platform.exit());
                stage.show();    
            }
            else
            {
                if(getConfig().isAllowDonatePopup())
                {
                    if(new Random().nextInt(5) == 3)
                        new DonatePopupContent(getHostServices(), this).show();
                }

                othInit();
            }
        }
        catch (SevereException e)
        {
            handleSevereException(e);
        }
    }

    @Override
    public void othInit()
    {
        try
        {
            if(StartupFlags.START_MINIMISED && SystemTray.isSupported())
                minimiseApp();
            else
                getStage().show();
        }
        catch(MinorException e)
        {
            handleMinorException(e);
        }

        executor.execute(new Task<Void>() {
            @Override
            protected Void call()
            {
                try
                {
                    getSettingsBase().getGeneralSettings().loadDataFromConfig();

                    //themes
                    getSettingsBase().getThemesSettings().setThemes(getThemes());
                    getSettingsBase().getThemesSettings().setCurrentThemeFullName(getCurrentTheme().getFullName());
                    getSettingsBase().getThemesSettings().loadThemes();

                    //clients
                    getSettingsBase().getClientsSettings().loadData();

                    try
                    {
                        //Plugins 
                        Platform.runLater(()->{
                            getDashboardBase().getPluginsPane().clearData();
                            getDashboardBase().getPluginsPane().loadOtherActions();
                        });

                        ExternalPlugins.setPluginsLocation(getConfig().getPluginsPath());
                        ExternalPlugins.getInstance().init();

                        Platform.runLater(()->getDashboardBase().getPluginsPane().loadData());

                        getSettingsBase().getPluginsSettings().loadPlugins();
                    }
                    catch (MinorException e)
                    {
                        getSettingsBase().getPluginsSettings().showPluginInitError();
                        handleMinorException(e);
                    }

                    //Server
                    mainServer.setPort(getConfig().getPort());
                    mainServer.start();
                }
                catch (SevereException e)
                {
                    handleSevereException(e);
                }
                return null;
            }
        });
    }

    @Override
    public void factoryReset()
    {
        getLogger().info("Reset to factory ...");

        onQuitApp();

        boolean result = IOHelper.deleteFile(getServerInfo().getPrePath());

        if(result)
        {
            getStage().close();
            init();
        }
        else
        {
            handleSevereException(new SevereException("Unable to delete all files successfully. Installation corrupt. Re-install."));
        }
    }

    private void setupSettingsWindowsAnimations()
    {
        Node settingsNode = getSettingsBase();
        Node dashboardNode = getDashboardBase();

        openSettingsAnimation = createOpenSettingsAnimation(settingsNode, dashboardNode);
        closeSettingsAnimation = createCloseSettingsAnimation(settingsNode, dashboardNode);
    }

    public void onCloseRequest(WindowEvent event)
    {
        try
        {
            if(Config.getInstance().getMinimiseToSystemTrayOnClose() &&
                    SystemTray.isSupported())
            {
                minimiseApp();
                event.consume();
                return;
            }

            onQuitApp();
            exit();
        }
        catch (SevereException e)
        {
            handleSevereException(e);
        }
        catch (MinorException e)
        {
            handleMinorException(e);
        }
    }

    public void onQuitApp()
    {
        getLogger().info("Shutting down ...");

        try
        {
            if(getConfig() != null)
            {
                getConfig().setStartupWindowSize(getWidth(), getHeight());
                getConfig().setRightDividerPositions(getDashboardBase().getDividerPositions());
                getConfig().setLeftDividerPositions(getDashboardBase().getLeftSplitPane().getDividerPositions());
                getConfig().save();
            }
        }
        catch (SevereException e)
        {
            handleSevereException(e);
        }

        stopServerAndAllConnections();
        executor.shutdown();
        ExternalPlugins.getInstance().shutDownActions();
        closeLogger();
        Config.nullify();
    }

    public void exit()
    {
        Platform.exit();
    }

    private void stopServerAndAllConnections()
    {
        if(mainServer!=null)
            mainServer.stopListeningForConnections();

        ClientConnections.getInstance().disconnectAll();
    }

    @Override
    public void restart()
    {
        getLogger().info("Restarting ...");

        stopServerAndAllConnections();
        Platform.runLater(this::init);
    }

    public void minimiseApp() throws MinorException
    {
        try
        {
            SystemTray systemTray = SystemTray.getSystemTray();

            if(getTrayIcon() == null)
                initIconTray(systemTray);

            systemTray.add(getTrayIcon());
            getStage().hide();

            getStage().setOnShown(windowEvent -> {
                systemTray.remove(getTrayIcon());
            });
        }
        catch(Exception e)
        {
            throw new MinorException(e.getMessage());
        }
    }

    public void initIconTray(SystemTray systemTray)
    {

        Platform.setImplicitExit(false);
        
        PopupMenu popup = new PopupMenu();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(l->{
            systemTray.remove(getTrayIcon());
            onQuitApp();
            exit();
        });

        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(
            Toolkit.getDefaultToolkit().getImage(Main.class.getResource("app_icon.png")),
            "Stream-Pi Server",
            popup
        );

        trayIcon.addActionListener(l-> Platform.runLater(()-> {
            getStage().show();
            getStage().setAlwaysOnTop(true);
            getStage().setAlwaysOnTop(false);
        }));

        trayIcon.setImageAutoSize(true);

        this.trayIcon = trayIcon;
    }

    private TrayIcon trayIcon = null;

    public TrayIcon getTrayIcon()
    {
        return trayIcon;
    }

    @Override
    public void handleMinorException(MinorException e)
    {
        getLogger().log(Level.SEVERE, e.getShortMessage(), e);
        e.printStackTrace();


        Platform.runLater(()-> new StreamPiAlert(e.getTitle(), e.getShortMessage(), StreamPiAlertType.WARNING).show());
    }

    @Override
    public void handleSevereException(SevereException e) {
        getLogger().log(Level.SEVERE, e.getShortMessage(), e);
        e.printStackTrace();
    
        Platform.runLater(()->{
            StreamPiAlert alert = new StreamPiAlert(e.getTitle(), e.getShortMessage(), StreamPiAlertType.ERROR);

            alert.setOnClicked(new StreamPiAlertListener()
            {
                @Override
                public void onClick(String txt)
                {    
                    onQuitApp();
                    exit();
                }
            });

            alert.show();
        });
    }

    @Override
    public void onActionClicked(Client client, String profileID, String actionID, boolean toggle)
    {
        try
        {
            Action action =  client.getProfileByID(profileID).getActionByID(actionID);

            if(action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.TOGGLE)
            {
                if(action.isInvalid())
                {
                    throw new MinorException(
                            "The action isn't installed on the server."
                    );
                }
                else
                {
                    executor.execute(new Task<Void>() {
                        @Override
                        protected Void call()
                        {
                            try {
                                Thread.sleep(action.getDelayBeforeExecuting());

                                getLogger().info("action "+action.getID()+" clicked!");

                                playSound();

                                if(action instanceof ToggleAction)
                                {
                                    onToggleActionClicked((ToggleAction) action, toggle, profileID,
                                            client.getRemoteSocketAddress());
                                }
                                else if (action instanceof NormalAction)
                                {
                                    onNormalActionClicked((NormalAction) action, profileID,
                                            client.getRemoteSocketAddress());
                                }
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                                getLogger().info("onActionClicked scheduled task was interrupted ...");
                            }
                            return null;
                        }
                    });
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            handleMinorException(new MinorException(e.getMessage()));
        }
    }

    public void playSound()
    {
        if(soundactivated)
        {
            try
            {
                String musicFile = System.getProperty("user.home") + "/Stream-Pi/Server/beep-boop.wav";     // For example
                Media sound = new Media(new File(musicFile).toURI().toString());
                MediaPlayer mediaPlayer = new MediaPlayer(sound);
                mediaPlayer.play();
            }
            catch(Exception e)
            {
                handleMinorException(new MinorException(e.getMessage()));
            }
        }
        else
        {
            return;
        }
    }

    public synchronized void onNormalActionClicked(NormalAction action, String profileID, SocketAddress socketAddress)
    {
        try
        {
            action.onActionClicked();
        }
        catch (MinorException e)
        {
            sendActionFailed(e, socketAddress, profileID, action.getID());
        }
    }

    public synchronized void onToggleActionClicked(ToggleAction action, boolean toggle, String profileID, SocketAddress socketAddress)
    {
        try
        {
            if(toggle)
            {
                action.onToggleOn();
            }
            else
            {
                action.onToggleOff();
            }
        }
        catch (MinorException e)
        {
            sendActionFailed(e, socketAddress, profileID, action.getID());
        }
    }

    @Override
    public void clearTemp() {
        Platform.runLater(() -> {
            getDashboardBase().getClientAndProfileSelectorPane().refresh();
            getDashboardBase().getActionGridPane().clear();
            getDashboardBase().getActionGridPane().setFreshRender(true);
            getDashboardBase().getActionDetailsPane().clear();
            getSettingsBase().getClientsSettings().loadData();
        });
    }

    @Override
    public void saveServerProperties()
    {
        try
        {
            ExternalPlugins.getInstance().saveServerSettings();
            getSettingsBase().getPluginsSettings().loadPlugins();
        } catch (MinorException e) {
            e.printStackTrace();
            handleMinorException(e);
        }
    }

    private void saveClientActionMain(String profileID, String actionID, SocketAddress socketAddress, boolean sendIcons)
    {
        try {
            ClientConnection clientConnection = ClientConnections.getInstance().getClientConnectionBySocketAddress(socketAddress);

            ClientProfile clientProfile = clientConnection.getClient().getProfileByID(profileID);

            Action action = clientProfile.getActionByID(actionID);
            clientConnection.saveActionDetails(profileID, action);

            if(sendIcons && action.isHasIcon())
            {
                saveAllIcons(profileID, actionID, socketAddress, false);
            }


            Platform.runLater(()->{
                try {

                    ActionBox actionBox = getDashboardBase().getActionGridPane().getActionBoxByIDAndProfileID(
                            action.getID(),
                            profileID
                    );

                    if(actionBox != null)
                    {
                        Platform.runLater(actionBox::init);
                    }

                    if(getDashboardBase().getActionDetailsPane().getAction() != null)
                    {
                        // This block is executed when no Action is selected.
                        if(getDashboardBase().getActionDetailsPane().getAction().getID().equals(actionID))
                        {
                            getDashboardBase().getActionDetailsPane().setAction(action);
                            getDashboardBase().getActionDetailsPane().refresh();
                        }
                    }


                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });
        }
        catch (SevereException e)
        {
            handleSevereException(e);
        }
    }

    @Override
    public void saveClientAction(String profileID, String actionID, SocketAddress socketAddress, boolean sendIcons, boolean runAsync)
    {
        if(runAsync)
        {
            executor.execute(new Task<Void>() {
                @Override
                protected Void call()
                {
                    saveClientActionMain(profileID, actionID, socketAddress, sendIcons);
                    return null;
                }
            });
        }
        else
        {
            saveClientActionMain(profileID, actionID, socketAddress, sendIcons);
        }
    }

    @Override
    public void saveAllIcons(String profileID, String actionID, SocketAddress socketAddress)
    {
        saveAllIcons(profileID, actionID, socketAddress, true);
    }


    public void saveAllIcons(String profileID, String actionID, SocketAddress socketAddress, boolean async)
    {
        if(async)
        {
            executor.execute(new Task<Void>() {
                @Override
                protected Void call()
                {
                    saveAllIconsMain(profileID, actionID, socketAddress);
                    return null;
                }
            });
        }
        else
        {
            saveAllIconsMain(profileID, actionID, socketAddress);
        }
    }

    private void saveAllIconsMain(String profileID, String actionID, SocketAddress socketAddress)
    {
        try {
            ClientConnection clientConnection = ClientConnections.getInstance().getClientConnectionBySocketAddress(socketAddress);

            ClientProfile clientProfile = clientConnection.getClient().getProfileByID(profileID);
            Action action = clientProfile.getActionByID(actionID);

            for(String eachState : action.getIcons().keySet())
            {
                clientConnection.sendIcon(profileID, actionID, eachState,
                        action.getIcon(eachState));
            }
        }
        catch (SevereException e)
        {
            handleSevereException(e);
        }
    }

    @Override
    public void saveIcon(String state, String profileID, String actionID, SocketAddress socketAddress) {
        executor.execute(new Task<Void>() {
            @Override
            protected Void call()
            {
                try {
                    ClientConnection clientConnection = ClientConnections.getInstance().getClientConnectionBySocketAddress(socketAddress);

                    ClientProfile clientProfile = clientConnection.getClient().getProfileByID(profileID);
                    Action action = clientProfile.getActionByID(actionID);

                    clientConnection.sendIcon(profileID, actionID, state, action.getIcon(state));
                }
                catch (SevereException e)
                {
                    handleSevereException(e);
                }
                return null;
            }
        });
    }

    @Override
    public com.stream_pi.util.platform.Platform getPlatform() {
        return ServerInfo.getInstance().getPlatform();
    }

    @Override
    public void sendActionFailed(MinorException exception, SocketAddress socketAddress, String profileID, String actionID)
    {
        if(exception.getTitle() != null)
        {
            exception.setShortMessage(exception.getTitle()+"\n"+exception.getShortMessage());
        }

        exception.setTitle("Error while running action");

        handleMinorException(exception);

        executor.execute(new Task<Void>() {
            @Override
            protected Void call()
            {
                try {
                    ClientConnections.getInstance().getClientConnectionBySocketAddress(socketAddress)
                            .sendActionFailed(profileID, actionID);
                }
                catch (SevereException e)
                {
                    handleSevereException(e);
                }
                return null;
            }
        });

    }

    private Animation createOpenSettingsAnimation(Node settingsNode, Node dashboardNode) {
        Timeline openSettingsTimeline = new Timeline();
        openSettingsTimeline.setCycleCount(1);

        openSettingsTimeline.getKeyFrames().addAll(
                new KeyFrame(Duration.millis(0.0D),
                        new KeyValue(settingsNode.opacityProperty(),
                                0.0D, Interpolator.EASE_IN),
                        new KeyValue(settingsNode.scaleXProperty(),
                                1.1D, Interpolator.EASE_IN),
                        new KeyValue(settingsNode.scaleYProperty(),
                                1.1D, Interpolator.EASE_IN),
                        new KeyValue(settingsNode.scaleZProperty(),
                                1.1D, Interpolator.EASE_IN)),
                new KeyFrame(Duration.millis(90.0D),
                        new KeyValue(settingsNode.opacityProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleXProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleYProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleZProperty(),
                                1.0D, Interpolator.LINEAR)),

                new KeyFrame(Duration.millis(0.0D),
                        new KeyValue(dashboardNode.opacityProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleXProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleYProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleZProperty(),
                                1.0D, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(90.0D),
                        new KeyValue(dashboardNode.opacityProperty(),
                                0.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleXProperty(),
                                0.9D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleYProperty(),
                                0.9D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleZProperty(),
                                0.9D, Interpolator.LINEAR))
        );

        openSettingsTimeline.setOnFinished(e -> settingsNode.toFront());
        return openSettingsTimeline;
    }

    private Animation createCloseSettingsAnimation(Node settingsNode, Node dashboardNode) {
        Timeline closeSettingsTimeline = new Timeline();
        closeSettingsTimeline.setCycleCount(1);

        closeSettingsTimeline.getKeyFrames().addAll(

                new KeyFrame(Duration.millis(0.0D),
                        new KeyValue(settingsNode.opacityProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleXProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleYProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleZProperty(),
                                1.0D, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(90.0D),
                        new KeyValue(settingsNode.opacityProperty(),
                                0.0D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleXProperty(),
                                1.1D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleYProperty(),
                                1.1D, Interpolator.LINEAR),
                        new KeyValue(settingsNode.scaleZProperty(),
                                1.1D, Interpolator.LINEAR)),

                new KeyFrame(Duration.millis(0.0D),
                        new KeyValue(dashboardNode.opacityProperty(),
                                0.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleXProperty(),
                                0.9D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleYProperty(),
                                0.9D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleZProperty(),
                                0.9D, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(90.0D),
                        new KeyValue(dashboardNode.opacityProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleXProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleYProperty(),
                                1.0D, Interpolator.LINEAR),
                        new KeyValue(dashboardNode.scaleZProperty(),
                                1.0D, Interpolator.LINEAR))

        );

        closeSettingsTimeline.setOnFinished(event1 -> {
            dashboardNode.toFront();
            executor.execute(new Task<Void>() {
                @Override
                protected Void call()  {
                    try {
                        getSettingsBase().getClientsSettings().loadData();

                        getSettingsBase().getGeneralSettings().loadDataFromConfig();
                        getSettingsBase().getPluginsSettings().loadPlugins();

                        getSettingsBase().getThemesSettings().setThemes(getThemes());
                        getSettingsBase().getThemesSettings().setCurrentThemeFullName(getCurrentTheme().getFullName());
                        getSettingsBase().getThemesSettings().loadThemes();

                        getSettingsBase().setDefaultTabToGeneral();
                    }
                    catch (SevereException e)
                    {
                        handleSevereException(e);
                    }
                    catch (MinorException e)
                    {
                        handleMinorException(e);
                    }
                    return null;
                }
            });
        });
        return closeSettingsTimeline;
    }

    @Override
    public void setToggleStatus(boolean currentStatus, String profileID, String actionID, SocketAddress clientSocketAddress)
            throws MinorException
    {
        ClientConnection clientConnection = ClientConnections.getInstance().getClientConnectionBySocketAddress(
                clientSocketAddress
        );

        if(clientConnection == null)
            throw new ClientNotFoundException("setToggleStatus failed because no client found with given socket address");

        new Thread(new Task<Void>() {
            @Override
            protected Void call()
            {
                try
                {
                    clientConnection.setToggleStatus(currentStatus, profileID, actionID);
                }
                catch (SevereException e)
                {
                    handleSevereException(e);
                }
                return null;
            }
        }).start();

    }
}
