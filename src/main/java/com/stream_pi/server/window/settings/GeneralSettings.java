package com.stream_pi.server.window.settings;

import com.stream_pi.server.controller.ServerListener;
import com.stream_pi.server.info.StartupFlags;
import com.stream_pi.server.io.Config;
import com.stream_pi.server.window.ExceptionAndAlertHandler;
import com.stream_pi.server.info.ServerInfo;

import com.stream_pi.util.alert.StreamPiAlert;
import com.stream_pi.util.alert.StreamPiAlertListener;
import com.stream_pi.util.alert.StreamPiAlertType;
import com.stream_pi.util.checkforupdates.CheckForUpdates;
import com.stream_pi.util.checkforupdates.UpdateHyperlinkOnClick;
import com.stream_pi.util.exception.MinorException;
import com.stream_pi.util.exception.SevereException;
import com.stream_pi.util.platform.PlatformType;
import com.stream_pi.util.startatboot.StartAtBoot;
import com.stream_pi.util.uihelper.HBoxWithSpaceBetween;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import org.controlsfx.control.ToggleSwitch;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.SystemTray;
import java.io.File;
import java.util.logging.Logger;

import com.stream_pi.server.controller.Controller;

public class GeneralSettings extends VBox {

    private final TextField serverNameTextField;
    private final TextField portTextField;
    private final TextField pluginsPathTextField;
    private final TextField themesPathTextField;
    private final TextField actionGridPaneActionBoxSize;
    private final TextField actionGridPaneActionBoxGap;
    private final ToggleSwitch startOnBootToggleSwitch;
    private final ToggleSwitch soundSwitch;
    private final HBoxWithSpaceBetween soundHBox;
    private final HBoxWithSpaceBetween startOnBootHBox;
    private final ToggleSwitch minimizeToSystemTrayOnCloseToggleSwitch;
    private final HBoxWithSpaceBetween minimizeToSystemTrayOnCloseHBox;
    private final ToggleSwitch showAlertsPopupToggleSwitch;
    private final HBoxWithSpaceBetween showAlertsPopupHBox;
    private final Button saveButton;
    private final Button checkForUpdatesButton;
    private final Button factoryResetButton;


    private Logger logger;

    private ExceptionAndAlertHandler exceptionAndAlertHandler;

    private ServerListener serverListener;

    private HostServices hostServices;

    public GeneralSettings(ExceptionAndAlertHandler exceptionAndAlertHandler, ServerListener serverListener, HostServices hostServices)
    {
        this.hostServices = hostServices;


        this.exceptionAndAlertHandler = exceptionAndAlertHandler;
        this.serverListener = serverListener;

        logger = Logger.getLogger(GeneralSettings.class.getName());

        serverNameTextField = new TextField();

        portTextField = new TextField();

        pluginsPathTextField = new TextField();

        themesPathTextField = new TextField();

        actionGridPaneActionBoxSize = new TextField();
        actionGridPaneActionBoxGap = new TextField();

        startOnBootToggleSwitch = new ToggleSwitch();
        startOnBootHBox = new HBoxWithSpaceBetween("Start on Boot", startOnBootToggleSwitch);
        startOnBootHBox.managedProperty().bind(startOnBootHBox.visibleProperty());

        soundSwitch= new ToggleSwitch();
        soundHBox = new HBoxWithSpaceBetween("Sound Confirmation Feedback", soundSwitch);

        minimizeToSystemTrayOnCloseToggleSwitch = new ToggleSwitch();
        minimizeToSystemTrayOnCloseHBox = new HBoxWithSpaceBetween("Minimise To Tray On Close", minimizeToSystemTrayOnCloseToggleSwitch);

        showAlertsPopupToggleSwitch = new ToggleSwitch();
        showAlertsPopupHBox = new HBoxWithSpaceBetween("Show Popup On Alert", showAlertsPopupToggleSwitch);

        checkForUpdatesButton = new Button("Check for updates");
        checkForUpdatesButton.setOnAction(event->checkForUpdates());

        factoryResetButton = new Button("Factory Reset");
        factoryResetButton.setOnAction(actionEvent -> onFactoryResetButtonClicked());

        getStyleClass().add("general_settings");

        prefWidthProperty().bind(widthProperty());
        setAlignment(Pos.CENTER);
        setSpacing(5);

        getChildren().addAll(
                getUIInputBox("Server Name", serverNameTextField),
                getUIInputBox("Port", portTextField),
                getUIInputBox("Grid Pane - Box Size", actionGridPaneActionBoxSize),
                getUIInputBox("Grid Pane - Box Gap", actionGridPaneActionBoxGap),
                getUIInputBoxWithDirectoryChooser("Plugins Path", pluginsPathTextField),
                getUIInputBoxWithDirectoryChooser("Themes Path", themesPathTextField),
                soundHBox,
                minimizeToSystemTrayOnCloseHBox,
                startOnBootHBox,
                showAlertsPopupHBox
        );

        serverNameTextField.setPrefWidth(200);

        saveButton = new Button("Save");
        saveButton.setOnAction(event->save());

        getChildren().addAll(factoryResetButton, checkForUpdatesButton, saveButton);

        setPadding(new Insets(10));


    }

    private void checkForUpdates()
    {
        new CheckForUpdates(checkForUpdatesButton,
                PlatformType.SERVER, ServerInfo.getInstance().getVersion(), new UpdateHyperlinkOnClick() {
            @Override
            public void handle(ActionEvent actionEvent) {
                hostServices.showDocument(getURL());
            }
        });
    }

    private HBox getUIInputBoxWithDirectoryChooser(String labelText, TextField textField)
    {
       HBox hBox = getUIInputBox(labelText, textField);
       hBox.setSpacing(5.0);

       TextField tf = (TextField) hBox.getChildren().get(2);
       tf.setPrefWidth(300);
       tf.setDisable(true);


       Button button = new Button();
       FontIcon fontIcon = new FontIcon("far-folder");
       button.setGraphic(fontIcon);

       button.setOnAction(event -> {
           DirectoryChooser directoryChooser = new DirectoryChooser();


           try {
               File selectedDirectory = directoryChooser.showDialog(getScene().getWindow());

               textField.setText(selectedDirectory.getAbsolutePath());
           }
           catch (NullPointerException e)
           {
               logger.info("No folder selected");
           }
       });

       hBox.getChildren().add(button);


       return hBox;
    }

    private HBox getUIInputBox(String labelText, TextField textField)
    {
        textField.setPrefWidth(100);

        Label label = new Label(labelText);
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);


        return new HBox(label, region, textField);
    }



    public void loadDataFromConfig() throws SevereException {
        Config config = Config.getInstance();

        Platform.runLater(()->
        {
            serverNameTextField.setText(config.getServerName());
            portTextField.setText(config.getPort()+"");
            pluginsPathTextField.setText(config.getPluginsPath());
            themesPathTextField.setText(config.getThemesPath());
            actionGridPaneActionBoxSize.setText(config.getActionGridActionSize()+"");
            actionGridPaneActionBoxGap.setText(config.getActionGridActionGap()+"");

            minimizeToSystemTrayOnCloseToggleSwitch.setSelected(config.getMinimiseToSystemTrayOnClose());
            showAlertsPopupToggleSwitch.setSelected(config.isShowAlertsPopup());
            startOnBootToggleSwitch.setSelected(config.getStartOnBoot());
        });
    }

    public void save()
    {
        new Thread(new Task<Void>() {
            @Override
            protected Void call()
            {
                try {
                    boolean toBeReloaded = false;
                    boolean dashToBeReRendered = false;

                    Platform.runLater(()->{
                        saveButton.setDisable(true);
                    });

                    String serverNameStr = serverNameTextField.getText();
                    String serverPortStr = portTextField.getText();
                    String pluginsPathStr = pluginsPathTextField.getText();
                    String themesPathStr = themesPathTextField.getText();

                    String actionGridActionBoxSize = actionGridPaneActionBoxSize.getText();
                    String actionGridActionBoxGap = actionGridPaneActionBoxGap.getText();

                    boolean minimizeToSystemTrayOnClose = minimizeToSystemTrayOnCloseToggleSwitch.isSelected();
                    boolean showAlertsPopup = showAlertsPopupToggleSwitch.isSelected();
                    boolean startOnBoot = startOnBootToggleSwitch.isSelected();
                    boolean soundFeedback = soundSwitch.isSelected();

                    Config config = Config.getInstance();

                    StringBuilder errors = new StringBuilder();


                    if(serverNameStr.isBlank())
                    {
                        errors.append("* Server Name cannot be blank.\n");
                    }
                    else
                    {
                        if(!config.getServerName().equals(serverNameStr))
                        {
                            toBeReloaded = true;
                        }
                    }


                    int serverPort=-1;
                    try {
                        serverPort = Integer.parseInt(serverPortStr);

                        if (serverPort < 1024)
                            errors.append("* Server Port must be more than 1024\n");
                        else if(serverPort > 65535)
                            errors.append("* Server Port must be lesser than 65535\n");

                        if(config.getPort()!=serverPort)
                        {
                            toBeReloaded = true;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        errors.append("* Server Port must be integer.\n");
                    }


                    int actionSize=-1;
                    try {
                        actionSize = Integer.parseInt(actionGridActionBoxSize);

                        if(config.getActionGridActionSize() != actionSize)
                        {
                            dashToBeReRendered = true;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        errors.append("* action Size must be integer.\n");
                    }


                    int actionGap=-1;
                    try {
                        actionGap = Integer.parseInt(actionGridActionBoxGap);

                        if(config.getActionGridActionGap() != actionGap)
                        {
                            dashToBeReRendered = true;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        errors.append("* action Gap must be integer.\n");
                    }

                    if(pluginsPathStr.isBlank())
                    {
                        errors.append("* Plugins Path must not be blank.\n");
                    }
                    else
                    {
                        if(!config.getPluginsPath().equals(pluginsPathStr))
                        {
                            toBeReloaded = true;
                        }
                    }

                    if(themesPathStr.isBlank())
                    {
                        errors.append("* Themes Path must not be blank.\n");
                    }
                    else
                    {
                        if(!config.getThemesPath().equals(themesPathStr))
                        {
                            toBeReloaded = true;
                        }
                    }

                    if(!errors.toString().isEmpty())
                    {
                        throw new MinorException("Uh Oh!", "Please rectify the following errors and try again :\n"+errors.toString());
                    }

                    if(config.getStartOnBoot() != startOnBoot)
                    {
                        if(StartupFlags.RUNNER_FILE_NAME == null)
                        {
                            new StreamPiAlert("Uh Oh", "No Runner File Name Specified as startup arguments. Cant set run at boot.", StreamPiAlertType.ERROR).show();
                            startOnBoot = false;
                        }
                        else
                        {
                            StartAtBoot startAtBoot = new StartAtBoot(PlatformType.SERVER, ServerInfo.getInstance().getPlatform());
                            if(startOnBoot)
                            {
                                startAtBoot.create(new File(StartupFlags.RUNNER_FILE_NAME));
                            }
                            else
                            {
                                boolean result = startAtBoot.delete();
                                if(!result)
                                    new StreamPiAlert("Uh Oh!", "Unable to delete starter file", StreamPiAlertType.ERROR).show();
                            }
                        }
                    }

                    if(minimizeToSystemTrayOnClose)
                    {
                        if(!SystemTray.isSupported()) 
                        {
                            StreamPiAlert alert = new StreamPiAlert("Not Supported", "Tray System not supported on this platform ", StreamPiAlertType.ERROR);
                            alert.show();

                            minimizeToSystemTrayOnClose = false;
                        }
                    }
                    
                    

                    config.setServerName(serverNameStr);
                    config.setServerPort(serverPort);
                    config.setActionGridGap(actionGap);
                    config.setActionGridSize(actionSize);
                    config.setPluginsPath(pluginsPathStr);
                    config.setThemesPath(themesPathStr);

                    config.setMinimiseToSystemTrayOnClose(minimizeToSystemTrayOnClose);
                    StreamPiAlert.setIsShowPopup(showAlertsPopup);
                    config.setShowAlertsPopup(showAlertsPopup);
                    config.setStartupOnBoot(startOnBoot);

                    config.save();


                    loadDataFromConfig();

                    if(toBeReloaded)
                    {
                        StreamPiAlert restartPrompt = new StreamPiAlert(
                                "Warning",
                                "Stream-Pi Server needs to be restarted for these changes to take effect. Restart?\n" +
                                        "All your current connections will be disconnected.",
                                StreamPiAlertType.WARNING
                        );

                        String yesOption = "Yes";
                        String noOption = "No";

                        restartPrompt.setButtons(yesOption, noOption);

                        restartPrompt.setOnClicked(new StreamPiAlertListener() {
                            @Override
                            public void onClick(String s) {
                                if(s.equals(yesOption))
                                {
                                    serverListener.restart();
                                }
                            }
                        });

                        restartPrompt.show();
                    }

                    if(dashToBeReRendered)
                    {
                        serverListener.clearTemp();
                    }

                    if(soundFeedback)
                    {
                        Controller con = new Controller();
                        con.soundactivated = true;
                    }
                }
                catch (MinorException e)
                {
                    exceptionAndAlertHandler.handleMinorException(e);
                }
                catch (SevereException e)
                {
                    exceptionAndAlertHandler.handleSevereException(e);
                }
                finally {
                    Platform.runLater(()->{
                        saveButton.setDisable(false);
                    });
                }
                return null;
            }
        }).start();
    }

    private void onFactoryResetButtonClicked()
    {
        StreamPiAlert confirmation = new StreamPiAlert("Warning","Are you sure?\n" +
                "This will erase everything.",StreamPiAlertType.WARNING);

        String yesButton = "Yes";
        String noButton = "No";

        confirmation.setButtons(yesButton, noButton);

        confirmation.setOnClicked(new StreamPiAlertListener() {
            @Override
            public void onClick(String s) {
                if(s.equals(yesButton))
                {
                    serverListener.factoryReset();
                }
            }
        });

        confirmation.show();
    }
}
