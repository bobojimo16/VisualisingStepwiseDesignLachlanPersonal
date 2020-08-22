package mc.client.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.stage.Window;
import lombok.Setter;
import mc.Constant;
import mc.util.expr.MyAssert;


import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Created by smithjord3 on 30/01/18.
 */
public class SettingsController implements Initializable {

    //

    private UserInterfaceController uic;

    @Setter
    private Window window;



    @FXML
    private Slider syncingEdgeSlider;

    @FXML
    private Slider processRelatingSlider;



    @FXML
    private Slider globalSlider;




    private double syncWeight;

    private double relatingWeight;
    private double globalWeight;

    private void handleButtonAction(ActionEvent e) {



    }

    @FXML
    private void handleSettingsConfirmation(ActionEvent e) {


        window.hide();
    }




    @Override
    public void initialize(URL location, ResourceBundle resources) {


        syncingEdgeSlider.valueProperty().addListener((arg0, arg1, newVal) -> {
            syncWeight = newVal.doubleValue();
            uic.changeSyncingEdgeWeight(syncWeight);
        });

        processRelatingSlider.valueProperty().addListener((arg0, arg1, newVal) -> {
            relatingWeight = newVal.doubleValue();
            uic.changeRelatingEdgeWeight(relatingWeight);
        });

        globalSlider.valueProperty().addListener((arg0, arg1, newVal) -> {
            globalWeight = newVal.doubleValue();
            uic.changeGlobalEdgeWeight(globalWeight);
        });



        // System.out.println("initialise Settings "+Symbolic +"  and "+isSymbolic());

    }


    @FXML
    public void setReferenceToUIC(UserInterfaceController userInterfaceController) {
        uic = userInterfaceController;
    }
}
