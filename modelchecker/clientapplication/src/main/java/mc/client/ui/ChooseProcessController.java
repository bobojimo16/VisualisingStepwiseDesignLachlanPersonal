package mc.client.ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class ChooseProcessController implements Initializable {

    @FXML
    public ComboBox SelectBox;
    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void setCombo(ArrayList<String> values){

        SelectBox.getItems().addAll(values);

    }

    @FXML
    private void doAcceptProcess(ActionEvent event) {
        Stage stage = (Stage) SelectBox.getScene().getWindow();
        stage.close();
    }

    public String getProcessValue(){
        return SelectBox.getValue().toString();
    }
}
