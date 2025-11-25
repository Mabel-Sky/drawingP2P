import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class applicationStateDialog {

    @FXML
    Button serverButton;

    @FXML
    Button clientServerButton;

    @FXML
    Button clientButton;

    Stage stage;
    int state = -1;
    public applicationStateDialog(String title) throws  IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("stateSelectUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle(title + " | state select");
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        serverButton.setOnMouseClicked(this::onServerButtonClick);
        clientServerButton.setOnMouseClicked(this::onServerClientButtonClick);
        clientButton.setOnMouseClicked(this::onClientButtonClick);

        stage.showAndWait();
    }

    @FXML
    void onServerButtonClick(Event event) {
        state = 0;
        stage.close();
    }

    @FXML
    void onServerClientButtonClick(Event event) {
        state = 1;
        stage.close();
    }

    @FXML
    void onClientButtonClick(Event event) {
        state = 2;
        stage.close();
    }
}
