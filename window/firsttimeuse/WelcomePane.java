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

package com.stream_pi.server.window.firsttimeuse;

import com.stream_pi.server.Main;
import com.stream_pi.server.combobox.LanguageChooserComboBox;
import com.stream_pi.server.controller.ServerListener;
import com.stream_pi.server.i18n.I18N;
import com.stream_pi.server.io.Config;
import com.stream_pi.server.window.ExceptionAndAlertHandler;
import com.stream_pi.util.exception.SevereException;
import com.stream_pi.util.uihelper.HBoxWithSpaceBetween;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.util.Objects;

public class WelcomePane extends VBox
{
    public WelcomePane(ExceptionAndAlertHandler exceptionAndAlertHandler, ServerListener serverListener)
    {
        getStyleClass().add("first_time_use_pane_welcome");

        Image appIcon = new Image(Objects.requireNonNull(Main.class.getResourceAsStream("icons/256x256.png")));
        ImageView appIconImageView = new ImageView(appIcon);
        VBox.setMargin(appIconImageView, new Insets(10, 0, 10, 0));
        appIconImageView.setFitHeight(128);
        appIconImageView.setFitWidth(128);


        Label welcomeLabel = new Label(I18N.getString("window.firsttimeuse.WelcomePane.welcome"));
        welcomeLabel.setWrapText(true);
        welcomeLabel.setAlignment(Pos.CENTER);
        welcomeLabel.getStyleClass().add("first_time_use_welcome_pane_welcome_label");

        Label nextToContinue = new Label(I18N.getString("window.firsttimeuse.WelcomePane.nextToContinue"));
        nextToContinue.setWrapText(true);
        nextToContinue.setAlignment(Pos.CENTER);
        nextToContinue.getStyleClass().add("first_time_use_welcome_pane_next_to_continue_label");

        LanguageChooserComboBox languageChooserComboBox = new LanguageChooserComboBox();
        languageChooserComboBox.getStyleClass().add("first_time_use_welcome_pane_language_chooser_combo_box");

        try
        {
            languageChooserComboBox.getSelectionModel().select(I18N.getLanguage(Config.getInstance().getCurrentLanguageLocale()));
        }
        catch (SevereException e)
        {
            exceptionAndAlertHandler.handleSevereException(e);
        }

        languageChooserComboBox.valueProperty().addListener((observableValue, oldVal, newVal) ->
        {
            try
            {
                if(oldVal!=newVal && newVal!=null)
                {
                    Config.getInstance().setCurrentLanguageLocale(newVal.getLocale());
                    Config.getInstance().save();
                    serverListener.restart();
                }
            }
            catch (SevereException e)
            {
                exceptionAndAlertHandler.handleSevereException(e);
            }
        });



        setAlignment(Pos.CENTER);
        setSpacing(5.0);
        getChildren().addAll(appIconImageView, welcomeLabel, nextToContinue, languageChooserComboBox);
    
        setVisible(false);
    }
}
