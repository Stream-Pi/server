/*
 * Stream-Pi - Free & Open-Source Modular Cross-Platform Programmable Macro Pad
 * Copyright (C) 2019-2021  Debayan Sutradhar (rnayabed),  Samuel Quiñones (SamuelQuinones)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.stream_pi.server.window.settings;

import com.stream_pi.action_api.actionproperty.property.*;
import com.stream_pi.action_api.externalplugin.ExternalPlugin;
import com.stream_pi.server.i18n.I18N;
import com.stream_pi.server.uipropertybox.UIPropertyBox;
import com.stream_pi.server.action.ExternalPlugins;
import com.stream_pi.server.controller.ServerListener;
import com.stream_pi.server.window.ExceptionAndAlertHandler;
import com.stream_pi.server.window.helper.Helper;
import com.stream_pi.util.exception.MinorException;
import com.stream_pi.util.uihelper.HBoxWithSpaceBetween;
import com.stream_pi.util.uihelper.SpaceFiller;

import javafx.geometry.Insets;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class PluginsSettings extends VBox
{

    private VBox pluginsSettingsVBox;

    private ServerListener serverListener;

    private Logger logger;

    private ExceptionAndAlertHandler exceptionAndAlertHandler;

    private HostServices hostServices;

    public PluginsSettings(ExceptionAndAlertHandler exceptionAndAlertHandler, HostServices hostServices)
    {
        this.hostServices = hostServices;
        this.exceptionAndAlertHandler = exceptionAndAlertHandler;
        pluginProperties = new ArrayList<>();
        logger = Logger.getLogger(PluginsSettings.class.getName());

        pluginsSettingsVBox = new VBox();
        pluginsSettingsVBox.getStyleClass().add("plugins_settings_vbox");
        pluginsSettingsVBox.setAlignment(Pos.TOP_CENTER);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.getStyleClass().add("plugins_settings_scroll_pane");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.maxWidthProperty().bind(widthProperty().multiply(0.8));

        pluginsSettingsVBox.prefWidthProperty().bind(scrollPane.widthProperty().subtract(25));
        scrollPane.setContent(pluginsSettingsVBox);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);



        saveButton = new Button(I18N.getString("save"));
        HBox.setMargin(saveButton, new Insets(0,10, 0, 0));
        saveButton.setOnAction(event -> onSaveButtonClicked());


        HBox hBox = new HBox(saveButton);
        hBox.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(scrollPane, hBox);
        getStyleClass().add("plugins_settings");
    }

    private Button saveButton;

    public void onSaveButtonClicked()
    {
        try {
            //form validation
            StringBuilder finalErrors = new StringBuilder();

            for (PluginProperties p : pluginProperties)
            {
                StringBuilder errors = new StringBuilder();
                for(int j = 0; j < p.getServerPropertyUIBox().size(); j++)
                {
                    UIPropertyBox serverProperty = p.getServerPropertyUIBox().get(j);
                    Node controlNode = serverProperty.getControlNode();


                    String value = ((TextField) controlNode).getText();
                    String error = Helper.validateProperty(value, serverProperty);

                    if (error != null)
                    {
                        errors.append("        -> ").append(error).append(("\n"));
                    }
                }

                if(!errors.toString().isBlank())
                {
                    finalErrors.append("    * ").append(p.getName()).append("\n").append(errors).append("\n");
                }
            }

            if(!finalErrors.toString().isEmpty())
            {
                throw new MinorException(I18N.getString("validationError", finalErrors));
            }

            //save
            for (PluginProperties pp : pluginProperties) {
                for (int j = 0; j < pp.getServerPropertyUIBox().size(); j++) {


                    UIPropertyBox serverProperty = pp.getServerPropertyUIBox().get(j);

                    String rawValue = serverProperty.getRawValue();

                    ExternalPlugins.getInstance().getActionFromIndex(pp.getIndex())
                            .getServerProperties().get()
                            .get(serverProperty.getIndex()).setRawValue(rawValue);
                }
            }


            ExternalPlugins.getInstance().saveServerSettings();

            ExternalPlugins.getInstance().initPlugins();
        }
        catch (MinorException e)
        {
            e.printStackTrace();
            exceptionAndAlertHandler.handleMinorException(e);
        }
    }

    private ArrayList<PluginProperties> pluginProperties;


    public void showPluginInitError()
    {
        Platform.runLater(()->{
            pluginsSettingsVBox.getChildren().add(new Label(I18N.getString("window.settings.PluginsSettings.pluginInitError")));
            saveButton.setVisible(false);
        });
    }

    public void loadPlugins() throws MinorException {

        pluginProperties.clear();

        List<ExternalPlugin> actions = ExternalPlugins.getInstance().getPlugins();

        Platform.runLater(()-> pluginsSettingsVBox.getChildren().clear());

        if(actions.size() == 0)
        {
            Platform.runLater(()->{
                Label l = new Label(I18N.getString("window.settings.PluginsSettings.noPluginsInstalled"));
                l.getStyleClass().add("plugins_pane_no_plugins_installed_label");
                pluginsSettingsVBox.getChildren().add(l);
                saveButton.setVisible(false);
            });
            return;
        }
        else
        {
            Platform.runLater(()->saveButton.setVisible(true));
        }


        for(int i = 0; i<actions.size(); i++)
        {
            ExternalPlugin action = actions.get(i);

            if(!action.isVisibleInServerSettingsPane())
                continue;


            Label headingLabel = new Label(action.getName());
            headingLabel.getStyleClass().add("plugins_settings_each_plugin_heading_label");

            HBox headerHBox = new HBox(headingLabel);
            headerHBox.getStyleClass().add("plugins_settings_each_plugin_header");


            if (action.getHelpLink()!=null)
            {
                Button helpButton = new Button();
                helpButton.getStyleClass().add("plugins_settings_each_plugin_help_button");
                FontIcon questionIcon = new FontIcon("fas-question");
                questionIcon.getStyleClass().add("plugins_settings_each_plugin_help_icon");
                helpButton.setGraphic(questionIcon);
                helpButton.setOnAction(event -> hostServices.showDocument(action.getHelpLink()));

                headerHBox.getChildren().addAll(SpaceFiller.horizontal() ,helpButton);
            }



            Label authorLabel = new Label(action.getAuthor());
            authorLabel.getStyleClass().add("plugins_settings_each_plugin_author_label");

            Label moduleLabel = new Label(action.getModuleName());
            moduleLabel.getStyleClass().add("plugins_settings_each_plugin_module_label");

            Label versionLabel = new Label(I18N.getString("version", action.getVersion().getText()));
            versionLabel.getStyleClass().add("plugins_settings_each_plugin_version_label");

            VBox serverPropertiesVBox = new VBox();
            serverPropertiesVBox.getStyleClass().add("plugins_settings_each_plugin_server_properties_box");
            serverPropertiesVBox.setSpacing(10.0);

            List<Property> serverProperties = action.getServerProperties().get();

            ArrayList<UIPropertyBox> serverPropertyArrayList = new ArrayList<>();


            for(int j =0; j<serverProperties.size(); j++)
            {
                Property eachProperty = serverProperties.get(j);

                if(!eachProperty.isVisible())
                    continue;
                Helper.ControlNodePair controlNodePair = new Helper().getControlNode(eachProperty);
                UIPropertyBox serverProperty = new UIPropertyBox(j, eachProperty.getDisplayName(), controlNodePair.getControlNode(), eachProperty.getControlType(), eachProperty.getType(), eachProperty.isCanBeBlank());
                serverPropertyArrayList.add(serverProperty);
                serverPropertiesVBox.getChildren().add(controlNodePair.getUINode());
            }

            PluginProperties pp = new PluginProperties(i, serverPropertyArrayList, action.getName());

            pluginProperties.add(pp);



            Platform.runLater(()->{
                VBox vBox = new VBox();
                vBox.getStyleClass().add("plugins_settings_each_plugin_box");
                vBox.setSpacing(5.0);
                vBox.getChildren().addAll(headerHBox, authorLabel, moduleLabel, versionLabel, serverPropertiesVBox);

                if(action.getServerSettingsButtonBar()!=null)
                {
                    action.getServerSettingsButtonBar().getStyleClass().add("plugins_settings_each_plugin_button_bar");
                    HBox buttonBarHBox = new HBox(SpaceFiller.horizontal(), action.getServerSettingsButtonBar());
                    buttonBarHBox.getStyleClass().add("plugins_settings_each_plugin_button_bar_hbox");
                    vBox.getChildren().add(buttonBarHBox);
                }

                pluginsSettingsVBox.getChildren().add(vBox);

            });
        }
    }

    public class PluginProperties
    {
        private int index;
        private ArrayList<UIPropertyBox> serverProperty;
        private String name;

        public PluginProperties(int index, ArrayList<UIPropertyBox> serverProperty, String name)
        {
            this.index = index;
            this.serverProperty = serverProperty;
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public ArrayList<UIPropertyBox> getServerPropertyUIBox() {
            return serverProperty;
        }
    }
}
