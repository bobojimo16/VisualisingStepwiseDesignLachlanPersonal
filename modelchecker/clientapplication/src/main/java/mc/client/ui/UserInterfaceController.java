package mc.client.ui;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
//import javafx.scene.shape.Shape;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import mc.client.ModelView;
import mc.client.VisualPetriToProcessCodeHelper;
import mc.compiler.CompilationObject;
import mc.compiler.CompilationObservable;
import mc.compiler.Compiler;
import mc.compiler.OperationResult;
import mc.exceptions.CompilationException;
import mc.util.LogMessage;
import mc.util.expr.Expression;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkDGS;
import org.graphstream.stream.file.FileSourceDGS;

import javax.swing.*;
import java.io.*;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static mc.client.ui.SyntaxHighlighting.computeHighlighting;


public class UserInterfaceController implements Initializable, FontListener {

    public AnchorPane modelDisplayNewContainer;
    public AnchorPane modelDisplayNewController;
    public Slider weightSlider;
    private boolean holdHighlighting = false; // If there is an compiler issue, highlight the area. Dont keep applying highlighting it wipes it out
    private javafx.stage.Popup autocompleteBox = new javafx.stage.Popup();
    private ExecutorService executor; // Runs the highlighting in separate ctx
    private TrieNode<String> completionDictionary;

    private SettingsController settingsController;
    private NewProcessController nameNewGraphElementController;
    private LabelEdgeController labelEdgeController;
    private NameNewParrelelProcessController nameNewParrelelProcessController;


    @FXML
    private CodeArea userCodeInput;
    @FXML
    private TextArea compilerOutputDisplay;

    @FXML
    private SwingNode modelDisplayNew;

    @FXML
    private ComboBox<String> modelsListNew;
    @FXML
    private MenuItem newMenuItem;
    @FXML
    private MenuItem openMenuItem;
    @FXML
    private MenuItem saveMenuItem;
    @FXML
    private MenuItem saveMenuItemDiagram;
    @FXML
    private Menu openRecentTab;
    @FXML
    private Button compileButton;

    @FXML
    private Button addBtnNew;

    @FXML
    private Button frzBtnNew;

    @FXML
    private Button unfrzBtnNew;

    @FXML
    private Button removeBtnNew;
    @FXML
    private Shape newAutomataNodeStart;
    @FXML
    private Shape newAutomataNodeNeutral;
    @FXML
    private Shape newAutomataNodeEnd;
    @FXML
    private Shape nextAutomataNode;
    @FXML
    private Shape newPetriPlaceStart;
    @FXML
    private Shape newPetriPlaceNeutral;
    @FXML
    private Shape newPetriPlaceEnd;
    @FXML
    private Shape nextPetriPlaceNode;
    @FXML
    private Shape newPetriTransition;
    @FXML
    private Shape nextPetriTransition;
    @FXML
    private Pane shapePane;
    @FXML
    private Button identifyPetriProcesses;


    // for keep updating the file that has already been saved.
    private File currentOpenFile = null;
    private boolean modified = false;

    private Thread buildThread = new Thread();

    private ArrayDeque<String> recentFilePaths = new ArrayDeque<>();
    private ArrayList<Shape> processShapesAuto = new ArrayList<Shape>();
    private ArrayList<Shape> processShapesPetri = new ArrayList<Shape>();

    private VisualPetriToProcessCodeHelper visualPetriToProcessCodeHelper;

    private ChooseProcessController chooseProcessController;
    private boolean typingInitiated = true;

    //@Getter
    //private static UserInterfaceController instance = this;

    @Override
    public void changeFontSize() {  // Lister Pattern
        int f = settingsController.getFont();
        String sfont = "-fx-font-size: " + f + "px;";
        //System.out.println("UserInterfaceController changeFontSize  "+sfont);
        userCodeInput.setStyle("-fx-background-color: #151515;" + sfont);
        compilerOutputDisplay.setStyle(sfont);
    }


    /**
     * Called to initialize a controller after its root element has been
     * completely processed.
     *
     * @param location  The location used to resolve relative paths for the root object, or
     *                  <tt>null</tt> if the location is not known.
     * @param resources The resources used to localize the root object, or <tt>null</tt> if
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

        String col = "rgba(153, 255, 204, 1.0)";
        try {


            //New Tab
            addBtnNew.setStyle("-fx-background-color: " + col);
            /*frzBtnNew.setStyle("-fx-background-color: " + col);
            unfrzBtnNew.setStyle("-fx-background-color: " + col);*/
            removeBtnNew.setStyle("-fx-background-color: " + col);
            modelsListNew.setStyle("-fx-background-color: " + col);

            //Load recent files
            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("recentfiles.conf")));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (!line.isEmpty())
                    addRecentFile(line);
            }
            bufferedReader.close();

        } catch (IOException e) {
            System.out.println("Error reading the settings file.");
        }

        settingsController = new SettingsController();
        settingsController.setReferenceToUIC(this);
        settingsController.addFontListener(this);

        ModelView.getInstance().setReferenceToUIC(this);

        ModelView.getInstance().setSettings(settingsController);
        // Have to initialise it or there is a delay between the graph becoming ready and actually displaying things
        SwingUtilities.invokeLater(() -> modelDisplayNew.setContent(ModelView.getInstance().updateGraphNew()));
        // SO the Swing node can be used as a canvas  HOW?

        //register a callback for whenever the list of automata is changed
        ModelView.getInstance().setListOfAutomataUpdater(this::updateModelsList);
        //register a callback for the output of the log
        /*  Now displaied as results are found -- stops long lag
        ModelView.getInstance().setUpdateLog(this::updateLogText);
        */
        //Add the key combinations
        newMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+N"));
        saveMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));
        openMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));

        //So the controller can save the settings we perhaps generate, we need to give it a reference to here
        UserInterfaceApplication.setController(this);


        //add all the syntax to the completion dictionary
        completionDictionary = new TrieNode<>(new ArrayList<>(Arrays.asList(SyntaxHighlighting.processTypes)));
        completionDictionary.add(new ArrayList<>(Arrays.asList(SyntaxHighlighting.functions)));
        completionDictionary.add(new ArrayList<>(Arrays.asList(SyntaxHighlighting.keywords)));

        //add style sheets
        //userCodeInput.setStyle("-fx-background-color: #32302f;");
        userCodeInput.setStyle("-fx-background-color: #151515;" + "-fx-font-size: 14px;");
        userCodeInput.getStylesheets().add(getClass().getResource("/clientres/automata-keywords.css").toExternalForm());

        ListView<String> popupSelection = new ListView<>();
        popupSelection.setStyle(
            "-fx-background-color: #f7e1a0;" +
                "-fx-text-fill:        black;" +
                "-fx-padding:          5;"
        );


        popupSelection.setOnMouseClicked(event -> {
            String selectedItem = popupSelection.getSelectionModel().getSelectedItem();
            actOnSelect(popupSelection, selectedItem);
        });

        popupSelection.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                String selectedItem = popupSelection.getSelectionModel().getSelectedItem();
                actOnSelect(popupSelection, selectedItem);
            }

        });

        autocompleteBox.getContent().add(popupSelection);
        autocompleteBox.setAutoHide(true);

        executor = Executors.newSingleThreadExecutor();


        userCodeInput.setParagraphGraphicFactory(LineNumberFactory.get(userCodeInput)); // Add line numbers

        userCodeInput.setOnMouseClicked(event -> { // If the user clicks outside of the autocompletion box they probably dont want it there...
            autocompleteBox.hide();
        });

        userCodeInput.richChanges() // Set up syntax highlighting in another ctx as regex finding can take a while.
            .filter(ch -> !ch.getInserted().equals(ch.getRemoved()) && !holdHighlighting) // Hold highlighting if we have an issue and have highlighted it, otherwise it gets wiped.
            .successionEnds(Duration.ofMillis(20))
            .supplyTask(this::computeHighlightingAsync)
            .awaitLatest(userCodeInput.richChanges())
            .filterMap(t -> {
                if (t.isSuccess()) {
                    return Optional.of(t.get());
                } else {
                    t.getFailure().printStackTrace();
                    return Optional.empty();
                }
            })
            .subscribe(this::applyHighlighting);

        userCodeInput.richChanges()
            .filter(ch -> !ch.getInserted().equals(ch.getRemoved()) && ch.getInserted().getStyleOfChar(0).isEmpty())
            .filter(ch -> ch.getInserted().getText().length() == 1)
            .subscribe((change) -> { // Hook for detecting user input, used for autocompletion as that happens quickly.
                modified = true;
                holdHighlighting = false;
                if (change.getRemoved().getText().length() == 0) {  // If this isnt a backspace character

                    String currentUserCode = userCodeInput.getText();


                    if (userCodeInput.getCaretPosition() < currentUserCode.length()) {

                        char currentCharacter = userCodeInput.getText().charAt(userCodeInput.getCaretPosition());
                        switch (currentCharacter) {
                            case '\n':
                            case '\t':
                            case ' ': { // If the user has broken off a word, dont continue autocompleting it.
                                autocompleteBox.hide();

                                popupSelection.getItems().clear();
                            }
                            break;

                            default: {
                                popupSelection.getItems().clear();
                                String currentWord = getWordAtIndex(userCodeInput.getCaretPosition());
                                if (currentWord.length() > 0) {
                                    ArrayList<String> list = completionDictionary.getId(currentWord);

                                    if (list.size() != 0) {
                                        popupSelection.getItems().addAll(list);
                                        popupSelection.getSelectionModel().select(0);

                                        if (userCodeInput.getCaretBounds().isPresent())

                                            autocompleteBox.setHeight(list.size() * 0.5);
                                        //System.out.println("contents = " + autocompleteBox.getContent().toString());
                                        //System.out.println("Height = " + autocompleteBox.getHeight());
                                        autocompleteBox.show(userCodeInput,
                                            (userCodeInput.getCaretBounds().get().getMaxX() + 20), //+x = right
                                            (userCodeInput.getCaretBounds().get().getMaxY() + 10)); //+y = down
                                        //Beware the Box moves up if near bottom Hence need the  move right
                                    } else { // If we dont have any autocomplete suggestions dont show the box
                                        autocompleteBox.hide();

                                    }
                                } else {
                                    autocompleteBox.hide();

                                }

                            }
                            break;
                        }
                    }

                } else { // Handles if there is a backspace
                    popupSelection.getItems().clear();
                    autocompleteBox.hide();
                }


            });

        userCodeInput.richChanges()
            .filter(ch -> ch.getInserted().getText().length() == 1)
            .subscribe((startedTyping) -> {
                if(typingInitiated) {
                    compilerOutputDisplay.appendText("Compiling..." + "\n");
                    typingInitiated = false;
                }

            });

        userCodeInput.richChanges().successionEnds(Duration.ofMillis(5000))

            .subscribe((finishedTyping) -> {
                compilerOutputDisplay.appendText("Recompiling..." + "\n");
                System.out.println("Recompile");
                typingInitiated = true;
                handleCompileRequest();

            });

        String userCode = "processes{\n" +
            "\n" +
            "A = a -> b -> STOP.\n" +
            "\n" +
            "\n" +
            "}";

        userCodeInput.appendText(userCode);

        //settingsController = new SettingsController();

        nameNewGraphElementController = new NewProcessController();
        chooseProcessController = new ChooseProcessController();
        labelEdgeController = new LabelEdgeController();
        nameNewParrelelProcessController = new NameNewParrelelProcessController();
        //newProcessController.initialize();


        //AddProcessShapesAutoInitial();
        AddProcessShapesPetriInitial();

        visualPetriToProcessCodeHelper = new VisualPetriToProcessCodeHelper();

        weightSlider.valueProperty().addListener((arg0, arg1, newVal) -> {
            Double weight = newVal.doubleValue();
            ModelView.getInstance().setGraphWeight(weight);
        });
    }


    private void AddProcessShapesAutoInitial() {
        int xTran = 20;
        newAutomataNodeStart = new Rectangle(60 - xTran, 75, 40, 40);
        newAutomataNodeNeutral = new Rectangle(110 - xTran, 75, 40, 40);
        newAutomataNodeEnd = new Rectangle(160 - xTran, 75, 40, 40);

        processShapesAuto.add(newAutomataNodeStart);
        processShapesAuto.add(newAutomataNodeNeutral);
        processShapesAuto.add(newAutomataNodeEnd);

        newAutomataNodeStart.setFill(Color.GREEN);
        newAutomataNodeNeutral.setFill(Color.GRAY);
        newAutomataNodeEnd.setFill(Color.RED);

        newAutomataNodeStart.setId("AutoStart");
        newAutomataNodeNeutral.setId("AutoNeutral");
        newAutomataNodeEnd.setId("AutoEnd");

        for (Shape s : processShapesAuto) {
            Rectangle c = (Rectangle) s;
            c.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragAuto);
            c.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressAuto);
            c.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleaseAuto);
            shapePane.getChildren().add(c);
        }

    }

    public void doMouseDragAuto(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {
            Rectangle c = (Rectangle) mouseEvent.getSource();
            double xOffset = mouseEvent.getX() - c.getX();
            double yOffset = mouseEvent.getY() - c.getY();
            c.setX(c.getX() + xOffset);
            c.setY(c.getY() + yOffset);
        }
    }

    public void doMousePressAuto(MouseEvent mouseEvent) {

        if(ModelView.getInstance().interactionType.equals("create")) {

            int xTran = 20;

            //todo add switch

            Rectangle c = (Rectangle) mouseEvent.getSource();


            if (c.getId().equals("AutoStart")) {
                nextAutomataNode = new Rectangle(60 - xTran, 75, 40, 40);
                nextAutomataNode.setFill(Color.GREEN);
                nextAutomataNode.setId("AutoStart");

            } else if (c.getId().equals("AutoNeutral")) {
                nextAutomataNode = new Rectangle(110 - xTran, 75, 40, 40);
                nextAutomataNode.setFill(Color.GRAY);
                nextAutomataNode.setId("AutoNeutral");
            } else {
                nextAutomataNode = new Rectangle(160 - xTran, 75, 40, 40);
                nextAutomataNode.setFill(Color.RED);
                nextAutomataNode.setId("AutoEnd");
            }

            nextAutomataNode.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragAuto);
            nextAutomataNode.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressAuto);
            nextAutomataNode.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleaseAuto);
            shapePane.getChildren().add(nextAutomataNode);
        }
    }


    public void doMouseReleaseAuto(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {

            Rectangle c = (Rectangle) mouseEvent.getSource();
            if (c.getId().equals("AutoStart")) {
                shapePane.getChildren().remove(newAutomataNodeStart);
                newAutomataNodeStart = nextAutomataNode;
            } else if (c.getId().equals("AutoNeutral")) {
                shapePane.getChildren().remove(newAutomataNodeNeutral);
                newAutomataNodeNeutral = nextAutomataNode;
            } else {
                shapePane.getChildren().remove(newAutomataNodeEnd);
                newAutomataNodeEnd = nextAutomataNode;
            }

            System.out.println("id: " + c.getId());

            addAutomataToGraph(c.getId(), mouseEvent);
        }

    }


    private void addAutomataToGraph(String nodeType, MouseEvent mouseEvent) {

        if(determineInBounds(mouseEvent)) {

            ModelView.getInstance().setNewVisualNodeType(nodeType);

            if (nodeType.equals("AutoStart")) {
                initiateNameNewGraphElementPopup();
            }
        }
    }

    private void AddProcessShapesPetriInitial() {


        newPetriPlaceStart = new Circle(60, 220, 20);
        newPetriPlaceNeutral = new Circle(110, 220, 20);
        newPetriPlaceEnd = new Circle(160, 220, 20);
        newPetriTransition = new Rectangle(90, 260, 40, 40);

        processShapesPetri.add(newPetriPlaceStart);
        processShapesPetri.add(newPetriPlaceNeutral);
        processShapesPetri.add(newPetriPlaceEnd);
        //processShapesPetri.add(newPetriTransition);

        newPetriPlaceStart.setFill(Color.GREEN);
        newPetriPlaceNeutral.setFill(Color.GRAY);
        newPetriPlaceEnd.setFill(Color.RED);
        newPetriTransition.setFill(Color.GRAY);

        newPetriPlaceStart.setId("PetriPlaceStart");
        newPetriPlaceNeutral.setId("PetriPlaceNeutral");
        newPetriPlaceEnd.setId("PetriPlaceEnd");
        newPetriTransition.setId("PetriTransition");

        for (Shape s : processShapesPetri) {

            Circle c = (Circle) s;
            c.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragPetriPlace);
            c.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressPetriPlace);
            c.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleasePetriPlace);
            shapePane.getChildren().add(c);
        }

        newPetriTransition.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragPetriTransition);
        newPetriTransition.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressPetriTransition);
        newPetriTransition.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleasePetriTransition);

        shapePane.getChildren().add(newPetriTransition);


    }

    private void doMouseDragPetriPlace(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {
            Circle c = (Circle) mouseEvent.getSource();
            double xOffset = mouseEvent.getX() - c.getCenterX();
            double yOffset = mouseEvent.getY() - c.getCenterY();
            c.setCenterX(c.getCenterX() + xOffset);
            c.setCenterY(c.getCenterY() + yOffset);
        }
    }

    private void doMousePressPetriPlace(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {
            //todo add switch

            Circle c = (Circle) mouseEvent.getSource();

            System.out.println(c);

            if (c.getId().equals("PetriPlaceStart")) {
                nextPetriPlaceNode = new Circle(60, 220, 20);
                nextPetriPlaceNode.setFill(Color.GREEN);
                nextPetriPlaceNode.setId("PetriPlaceStart");

            } else if (c.getId().equals("PetriPlaceNeutral")) {
                nextPetriPlaceNode = new Circle(110, 220, 20);
                nextPetriPlaceNode.setFill(Color.GRAY);
                nextPetriPlaceNode.setId("PetriPlaceNeutral");
            } else {
                nextPetriPlaceNode = new Circle(160, 220, 20);
                nextPetriPlaceNode.setFill(Color.RED);
                nextPetriPlaceNode.setId("PetriPlaceEnd");
            }

            nextPetriPlaceNode.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragPetriPlace);
            nextPetriPlaceNode.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressPetriPlace);
            nextPetriPlaceNode.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleasePetriPlace);
            shapePane.getChildren().add(nextPetriPlaceNode);
        }

    }

    public void doMouseReleasePetriPlace(MouseEvent mouseEvent) {


        if(ModelView.getInstance().interactionType.equals("create")) {

            Circle c = (Circle) mouseEvent.getSource();
            if (c.getId().equals("PetriPlaceStart")) {
                shapePane.getChildren().remove(newPetriPlaceStart);
                newPetriPlaceStart = nextPetriPlaceNode;
            } else if (c.getId().equals("PetriPlaceNeutral")) {
                shapePane.getChildren().remove(newPetriPlaceNeutral);
                newPetriPlaceNeutral = nextPetriPlaceNode;
            } else {
                shapePane.getChildren().remove(newPetriPlaceEnd);
                newPetriPlaceEnd = nextPetriPlaceNode;
            }

            addPetriPlaceToGraph(c.getId(), mouseEvent);
        }

    }


    public void doMouseDragPetriTransition(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {
            Rectangle r = (Rectangle) mouseEvent.getSource();
            double xOffset = mouseEvent.getX() - r.getX();
            double yOffset = mouseEvent.getY() - r.getY();
            r.setX(r.getX() + xOffset);
            r.setY(r.getY() + yOffset);
        }
    }

    public void doMousePressPetriTransition(MouseEvent mouseEvent) {
        //todo add switch
        if(ModelView.getInstance().interactionType.equals("create")) {
            nextPetriTransition = new Rectangle(90, 260, 40, 40);
            nextPetriTransition.setFill(Color.GRAY);
            nextPetriTransition.setId("PetriTransition");


            nextPetriTransition.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::doMouseDragPetriTransition);
            nextPetriTransition.addEventHandler(MouseEvent.MOUSE_PRESSED, this::doMousePressPetriTransition);
            nextPetriTransition.addEventHandler(MouseEvent.MOUSE_RELEASED, this::doMouseReleasePetriTransition);
            shapePane.getChildren().add(nextPetriTransition);
        }

    }

    public void doMouseReleasePetriTransition(MouseEvent mouseEvent) {
        if(ModelView.getInstance().interactionType.equals("create")) {
            if (ModelView.getInstance().interactionType.equals("create")) {
                shapePane.getChildren().remove(newPetriTransition);
                newPetriTransition = nextPetriTransition;
                addPetriTransitionToGraph(mouseEvent);
            }
        }
    }

    private void addPetriPlaceToGraph(String petriPlaceType, MouseEvent mouseEvent) {

        if(determineInBounds(mouseEvent)) {

            ModelView.getInstance().setNewVisualNodeType(petriPlaceType);

            if (petriPlaceType.equals("PetriPlaceStart")) {
                initiateNameNewGraphElementPopup();
            }
        }

    }

    private Boolean determineInBounds(MouseEvent mouseEvent){
        return mouseEvent.getSceneY() > modelDisplayNewContainer.getLayoutY()+modelDisplayNewController.getHeight()
            && mouseEvent.getSceneY() < modelDisplayNewContainer.getLayoutY() + modelDisplayNewContainer.getHeight()
            && mouseEvent.getSceneX() > 0
            && mouseEvent.getSceneX() < shapePane.getLayoutX();
    }

    private void addPetriTransitionToGraph(MouseEvent mouseEvent) {

        if(determineInBounds(mouseEvent)) {
            ModelView.getInstance().setNewVisualNodeType("petriTransition");
            initiateNameNewGraphElementPopup();
        }
    }

    private void initiateNameNewGraphElementPopup() {


        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clientres/NameNewGraphElement.fxml"));
        loader.setController(nameNewGraphElementController); //links to  SettingsController.java
        try {
            Stage newProcessStage = new Stage();
            newProcessStage.setTitle("New Process");

            Scene windowScene = new Scene(loader.load(), 402, 326);
            newProcessStage.setScene(windowScene);

            //settingsController.setWindow(newProcessStage.getScene().getWindow());
            newProcessStage.initOwner(UserInterfaceApplication.getPrimaryStage());
            //settingsStage.initModality(Modality.APPLICATION_MODAL);
            newProcessStage.initModality(Modality.NONE);
            newProcessStage.setResizable(false);

            CountDownLatch latch = new CountDownLatch(1);
            newProcessStage.showAndWait();
            latch.countDown();

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            ModelView.getInstance().setLatestNodeName(nameNewGraphElementController.getNewProcessNameValue());

        } catch (IOException e) {
            Alert optionsLayoutLoadFailed = new Alert(Alert.AlertType.ERROR);
            optionsLayoutLoadFailed.setTitle("Error encountered when loading new proccess dialogue");
            optionsLayoutLoadFailed.setContentText("Error: " + e.getMessage());

            optionsLayoutLoadFailed.initModality(Modality.APPLICATION_MODAL);
            optionsLayoutLoadFailed.initOwner(modelDisplayNew.getScene().getWindow());

            optionsLayoutLoadFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            optionsLayoutLoadFailed.show();
        }
    }

    public String nameEdge() {


        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clientres/LabelEdgePopup.fxml"));
        loader.setController(labelEdgeController); //links to  SettingsController.java
        String toReturn = "";
        try {
            Stage newProcessStage = new Stage();
            newProcessStage.setTitle("Label Edge");

            Scene windowScene = new Scene(loader.load(), 402, 326);
            newProcessStage.setScene(windowScene);

            //settingsController.setWindow(newProcessStage.getScene().getWindow());
            newProcessStage.initOwner(UserInterfaceApplication.getPrimaryStage());
            //settingsStage.initModality(Modality.APPLICATION_MODAL);
            newProcessStage.initModality(Modality.NONE);
            newProcessStage.setResizable(false);
            newProcessStage.showAndWait();
            toReturn = labelEdgeController.getLabelNameValue();

        } catch (IOException e) {
            Alert optionsLayoutLoadFailed = new Alert(Alert.AlertType.ERROR);
            optionsLayoutLoadFailed.setTitle("Error encountered when loading new proccess dialogue");
            optionsLayoutLoadFailed.setContentText("Error: " + e.getMessage());

            optionsLayoutLoadFailed.initModality(Modality.APPLICATION_MODAL);
            optionsLayoutLoadFailed.initOwner(modelDisplayNew.getScene().getWindow());

            optionsLayoutLoadFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            optionsLayoutLoadFailed.show();
        }

        return toReturn;


    }

    public void reportError(String errorType) {
        Alert a = new Alert(Alert.AlertType.NONE);
        a.setAlertType(Alert.AlertType.WARNING);

        switch (errorType) {
            case "transitionBetweenAutoAndPetri":
                a.setContentText("Unable To Place Transitions Between Automata and Petri-Net Nodes");
                break;
            case "petriEdgeNoTransition":
                a.setContentText("Unable To Connect Places Without A Transition - Try Using A Transition");
                break;
            case "petriEdgeBothTransitions":
                a.setContentText("Unable To Connect Transitions - Try Using A Place");
                break;
            case "petriTransitionBranching":
                a.setContentText("Unable To Branch On A Transition - Try Branching On A Place");
                break;
            case "petriSingleProcessTransitionMultipleEntries":
                a.setContentText("Transition unable to have multiple entries from petri elements of the same process");
                break;
            case "backwardsPetriBuilding":
                a.setContentText("Unable to build Petri-Nets backwards, make sure the process contains a start place");
                break;
            case "deletingNonLeaf":
                a.setContentText("Unable to delete non leaf nodes");
            case "petriTransitionMoreOutGoingThanIn":
                a.setContentText("Unable to have transitions with more outgoing edges than incoming edges");
            case "syncingOnAPetriPlace":
                a.setContentText("Unable To Sync On a Place");
            default:
                break;
        }


        a.show();
    }

    /**
     * This is a helper function to add an insert
     *
     * @param popupSelection
     * @param selectedItem
     */
    private void actOnSelect(ListView<String> popupSelection, String selectedItem) {
        if (selectedItem != null) {

            String code = userCodeInput.getText();
            int wordPosition = userCodeInput.getCaretPosition() - 1; // we know where the user word is, but we dont know the start or end

            int start;
            for (start = wordPosition;
                 start > 0 &&
                     !Character.isWhitespace(code.charAt(start - 1)) &&
                     Character.isLetterOrDigit(userCodeInput.getText().charAt(start - 1));
                 start--)
                ;

            int end;
            for (end = wordPosition;
                 end < code.length() &&
                     !Character.isWhitespace(code.charAt(end)) &&
                     Character.isLetterOrDigit(userCodeInput.getText().charAt(end));
                 end++)
                ;


            userCodeInput.replaceText(start, end, selectedItem);

            popupSelection.getItems().clear();
            autocompleteBox.hide();

            userCodeInput.setStyleSpans(0, computeHighlighting(userCodeInput.getText())); // Need to reupdate the styles when an insert has happened.
        }
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = userCodeInput.getText();
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                return computeHighlighting(text);
            }
        };
        executor.execute(task);
        return task;
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        userCodeInput.setStyleSpans(0, highlighting); // Fires a style event
    }


    private String getWordAtIndex(int pos) {
        String text = userCodeInput.getText().substring(0, pos);

        // keeping track of index
        int index;

        // get first whitespace "behind caret"
        for (index = text.length() - 1;
             index >= 0 &&
                 !Character.isWhitespace(text.charAt(index)) &&
                 Character.isLetterOrDigit(userCodeInput.getText().charAt(index));
             index--)
            ;

        // get prefix and startIndex of word
        String prefix = text.substring(index + 1, text.length());

        // get first whitespace forward from caret
        for (index = pos;
             index < userCodeInput.getLength() &&
                 !Character.isWhitespace(userCodeInput.getText().charAt(index)) &&
                 Character.isLetterOrDigit(userCodeInput.getText().charAt(index));
             index++)
            ;

        String suffix = userCodeInput.getText().substring(pos, index);

        // replace regex wildcards (literal ".") with "\.". Looks weird but
        // correct...
        prefix = prefix.replaceAll("\\.", "\\.");
        suffix = suffix.replaceAll("\\.", "\\.");

        // combine both parts of words
        prefix = prefix + suffix;

        // return current word being typed
        return prefix;
    }

    boolean saveUserChanges() {
        if (modified) {

            Alert save = new Alert(Alert.AlertType.NONE);

            save.setTitle("Current file is modified");
            save.setContentText("Would you like to save changes?");

            ButtonType confirmSave = new ButtonType("Save", ButtonBar.ButtonData.YES);
            ButtonType dismissSave = new ButtonType("Dont save", ButtonBar.ButtonData.NO);
            ButtonType cancelOperation = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            save.getButtonTypes().setAll(confirmSave, dismissSave, cancelOperation);
            save.initModality(Modality.APPLICATION_MODAL); /* *** */

            save.showAndWait();

            ButtonType result = save.getResult();
            if (result == confirmSave) {
                File selectedFile = currentOpenFile;

                if (selectedFile == null) {

                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Save file");
                }

                if (selectedFile != null) { // Can still be null if they dont select anything in the saveDialog
                    try {
                        PrintStream writeTo = new PrintStream(selectedFile, "UTF-8");
                        writeTo.println(userCodeInput.getText());
                        writeTo.close();

                    } catch (IOException message) {
                        Alert saveFailed = new Alert(Alert.AlertType.ERROR);
                        saveFailed.setTitle("Error encountered when saving file");
                        saveFailed.setContentText("Error: " + message.getMessage());

                        saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
                        saveFailed.initModality(Modality.APPLICATION_MODAL);
                        saveFailed.show();
                    }
                }

            } else if (result != dismissSave) { // If the user has pressed cancel
                return false;
            }
        }

        return true;
    }


    @FXML
    private void handleCreateNew(ActionEvent event) {

        if (saveUserChanges()) {
            currentOpenFile = null;
            userCodeInput.clear();
            modified = false;
            holdHighlighting = false;
            UserInterfaceApplication.getPrimaryStage().setTitle("Process Modeller - New File");
        }
    }

    @FXML
    private void handleOpenDiagram(ActionEvent event) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save as");
            File selectedFile = chooser.showOpenDialog(null);
            MultiGraph loadedGraph = new MultiGraph("WorkingCanvasArea");
            FileSourceDGS fsdgs = new FileSourceDGS();
            fsdgs.addSink(loadedGraph);


            fsdgs.readAll(selectedFile.getAbsolutePath());

            ModelView.getInstance();

            SwingUtilities.invokeLater(() -> modelDisplayNew.setContent(ModelView.getInstance().setLoadedGraph(loadedGraph)));


        } catch (IOException e) {
            Alert saveFailed = new Alert(Alert.AlertType.ERROR);
            saveFailed.setTitle("Error encountered when reading file");
            saveFailed.setContentText("Error: " + e.getMessage());

            saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            saveFailed.initModality(Modality.APPLICATION_MODAL);
            saveFailed.show();
        } catch (RuntimeException e) {
            Alert saveFailed = new Alert(Alert.AlertType.ERROR);
            saveFailed.setTitle("Error encountered when reading file");
            saveFailed.setContentText("Error: " + e.getMessage());

            saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            saveFailed.initModality(Modality.APPLICATION_MODAL);
            saveFailed.show();
        }


    }

    private void openFile(String filePath) {

        if (saveUserChanges()) {
            try {
                File selectedFile = null;

                if (filePath != null) {
                    selectedFile = new File(filePath);
                } else {
                    FileChooser openDialog = new FileChooser();
                    openDialog.setTitle("Open file");
                }

                if (selectedFile != null) {
                    String data = Files.toString(selectedFile, Charsets.UTF_8);

                    userCodeInput.replaceText(data);
                    currentOpenFile = selectedFile;
                    UserInterfaceApplication.getPrimaryStage().setTitle("Process Modeller - " + currentOpenFile.getName());
                    modified = false;
                    holdHighlighting = false;
                    addRecentFile(selectedFile.getAbsolutePath());
                }

            } catch (IOException e) {
                Alert saveFailed = new Alert(Alert.AlertType.ERROR);
                saveFailed.setTitle("Error encountered when reading file");
                saveFailed.setContentText("Error: " + e.getMessage());

                saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
                saveFailed.initModality(Modality.APPLICATION_MODAL);
                saveFailed.show();
            }
        }
    }


    @FXML
    private void handleOpen(ActionEvent event) {
        openFile(null);
    }

    @FXML
    private void handleFileClose(ActionEvent event) {
        if (saveUserChanges()) {
            userCodeInput.clear();
            currentOpenFile = null;
            modified = false;

            UserInterfaceApplication.getPrimaryStage().setTitle("Process Modeller - New File");
        }
    }

    @FXML
    private void handleSave(ActionEvent event) {
        File selectedFile = currentOpenFile;

        if (selectedFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save file");
        }

        if (selectedFile != null) {
            try {
                PrintStream writeTo = new PrintStream(selectedFile, "UTF-8");
                writeTo.println(userCodeInput.getText());
                modified = false;
                currentOpenFile = selectedFile;
                UserInterfaceApplication.getPrimaryStage().setTitle("Process Modeller - " + currentOpenFile.getName());
                addRecentFile(selectedFile.getAbsolutePath());
            } catch (IOException e) {
                Alert saveFailed = new Alert(Alert.AlertType.ERROR);
                saveFailed.setTitle("Error encountered when saving file");
                saveFailed.setContentText("Error: " + e.getMessage());

                saveFailed.initModality(Modality.APPLICATION_MODAL);

                saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
                saveFailed.show();
            }
        }
    }

    @FXML
    private void handleSaveDiagram(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save as");
        File selectedFile = chooser.showSaveDialog(null);


        if (selectedFile != null) {


            FileSinkDGS fsdgs = new FileSinkDGS();
            try {
                fsdgs.writeAll(ModelView.getInstance().getGraph(), selectedFile.getAbsolutePath());
            } catch (IOException e) {
                Alert saveFailed = new Alert(Alert.AlertType.ERROR);
                saveFailed.setTitle("Error encountered when saving diagram");
                saveFailed.setContentText("Error: " + e.getMessage());

                saveFailed.initModality(Modality.APPLICATION_MODAL);

                saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
                saveFailed.show();
            }


        }
    }


    @FXML
    private void handleSaveAs(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save as");
        File selectedFile = chooser.showSaveDialog(null);

        if (selectedFile != null) {
            try {
                PrintStream writeTo = new PrintStream(selectedFile, "UTF-8");
                writeTo.println(userCodeInput.getText());
                currentOpenFile = selectedFile;
                UserInterfaceApplication.getPrimaryStage().setTitle("Process Modeller - " + currentOpenFile.getName());
                modified = false;
                addRecentFile(selectedFile.getAbsolutePath());

            } catch (IOException e) {
                Alert saveFailed = new Alert(Alert.AlertType.ERROR);
                saveFailed.setTitle("Error encountered when saving file");
                saveFailed.setContentText("Error: " + e.getMessage());

                saveFailed.initModality(Modality.APPLICATION_MODAL);

                saveFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
                saveFailed.show();
            }

        }

    }

    @FXML
    private void handleQuit(ActionEvent event) {
        if (saveUserChanges()) {
            UserInterfaceApplication.getPrimaryStage().hide();
        }
    }

    /*
       This is the Add button
     */


    @FXML
    private void handleAddSelectedModelNew(ActionEvent event) {
        //New Tab

        if (modelsListNew.getSelectionModel().getSelectedItem() != null && modelsListNew.getSelectionModel().getSelectedItem() instanceof String) {


            ModelView.getInstance().addDisplayedModel(modelsListNew.getSelectionModel().getSelectedItem());
            SwingUtilities.invokeLater(() -> modelDisplayNew.setContent(ModelView.getInstance().updateGraphNew()));

            //dstr ProcessModel pm = ModelView.getInstance().getProcess(modelsList.getSelectionModel().getSelectedItem());

            //  GraphView gv = new GraphView(pm);
            // SwingUtilities.invokeLater(() -> newDisplay.setContent(gv.display(newDisplay)));
        }
    }


    @FXML
    private void handleAddallModelsNew(ActionEvent event) {
        ModelView.getInstance().addAllModelsNew();
        SwingUtilities.invokeLater(() -> modelDisplayNew.setContent(ModelView.getInstance().updateGraphNew()));
    }


    @FXML
    public void handleClearNew(ActionEvent actionEvent) {
        //  ModelView.getInstance().clearDisplayed();
        String selecteditem = modelsListNew.getSelectionModel().getSelectedItem();
        if (selecteditem != null) {
            ModelView.getInstance().removeProcessModelNew(selecteditem);
        }
        //   SwingUtilities.invokeLater(() -> modelDisplay.setContent(ModelView.getInstance().updateGraph(modelDisplay)));

    }


    @FXML
    private void handleFreezeAllNew(ActionEvent event) {
        ModelView.getInstance().freezeAllCurrentlyDisplayedNew();
    }

    @FXML
    private void handleUnfreezeAllNew(ActionEvent event) {
        ModelView.getInstance().unfreezeAllCurrentlyDisplayedNew();
    }

    @FXML
    private void handleClearGraphNew(ActionEvent event) {
        ModelView.getInstance().clearDisplayedNew();
        SwingUtilities.invokeLater(() -> modelDisplayNew.setContent(ModelView.getInstance().updateGraphNew()));
    }


    @FXML
    private void handleOptions(ActionEvent event) {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clientres/SettingsInterface.fxml"));

        loader.setController(settingsController); //links to  SettingsController.java
        try {
            Stage settingsStage = new Stage();
            settingsStage.setTitle("Settings");

            Scene windowScene = new Scene(loader.load(), 400, 400);
            settingsStage.setScene(windowScene);

            settingsController.setWindow(settingsStage.getScene().getWindow());
            settingsStage.initOwner(UserInterfaceApplication.getPrimaryStage());
            //settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.initModality(Modality.NONE);
            settingsStage.setResizable(false);
            settingsStage.show();

        } catch (IOException e) {
            Alert optionsLayoutLoadFailed = new Alert(Alert.AlertType.ERROR);
            optionsLayoutLoadFailed.setTitle("Error encountered when loading options panel layout");
            optionsLayoutLoadFailed.setContentText("Error: " + e.getMessage());

            optionsLayoutLoadFailed.initModality(Modality.APPLICATION_MODAL);

            optionsLayoutLoadFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            optionsLayoutLoadFailed.show();
        }
    }

    //TODO: make this a better concurrent process
    @FXML
    private void handleCompileRequest() {
        String userCode = userCodeInput.getText();


        if (!userCode.isEmpty()) {

            if (buildThread.isAlive()) {
                //buildThread.stop();
                compilerOutputDisplay.appendText("Build cancelled" + "\n");
                compileButton.setText("Compile");

            } else {
                // ModelView.getInstance().cleanData();  // dstr try to fix data buildup 1/11/19
                compilerOutputDisplay.clear();
                compilerOutputDisplay.appendText("Starting build..." + "\n");

                modelsListNew.getItems().clear();

/*
     buildThread  runs in seerate thread and creats a  second Thread  to
     process message logging. This inturn
     uses - runLater to append to the javaFX event Queue  text to the display
 */
                String finalUserCode = userCode;
                buildThread = new Thread(() -> {
                    //can be used by multiple produces and multiple consumers
                    // log message for progress and  thread name
                    BlockingQueue<Object> messageLog = new LinkedBlockingQueue<>();
                    try {
                        // This follows the observer pattern.
                        // Within the compile function the code is then told to update an observer
                        Compiler codeCompiler = new Compiler();

                        //Try useing blocking queue to wait indefinately and
                        // use interupt to terminate
                        // OR use Executor with built in thread pool
                        // ExecutorService  Executors.newWorkStealingPool() - one thread per core
                        // CRAP   the evaluation of different equations should not interfere with each other
                        // hence work stealing (one queue per thread -to avoid contention) + stealin work
                        // when finished.
                        Thread logThread = new Thread(() -> {
                            while (true) { // Realitively expensive spinning lock

                                if (!messageLog.isEmpty()) {
                                    LogMessage t = ((LogMessage) messageLog.poll());
                                    System.out.println("Log " + t.getMessage());
                                    Platform.runLater(() -> {
                                        compilerOutputDisplay.appendText("** " + t.getMessage() + "\n");
                                    });
                                }
                                try {
                                    Thread.sleep(10); // To stop free spinning eating cpu
                                } catch (InterruptedException e) {
                                }
                            }
                        });
// Daemon threads can keep working after program terminates
// Processing Equations can take many minuites so we have a need to both
//    record the progress and
//    be able terminate long running builds
                        logThread.setDaemon(true); // Means the ctx doesnt hang the appication on close
                        logThread.start();

                        Supplier<Boolean> getSymb = () -> settingsController.isSymbolic();
                        //Keep the actual compilition outside the javafx ctx otherwise we get hanging
                        boolean s = settingsController.isSymbolic();
                        CompilationObject compilerOutput = codeCompiler.compile(finalUserCode, Expression.mkCtx(), messageLog, getSymb);

                        Platform.runLater(() -> {
                            CompilationObservable.getInstance().updateClient(compilerOutput);
                            // If this is run outside the fx ctx then exceptions occur and weirdness with threads updating combox box and whatnot
                            compilerOutputDisplay.appendText("Compiling completed!\n" + new Date().toString() + "\n");
                        });
                        //logThread.stop();

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (CompilationException e) {

                        Platform.runLater(() -> { // Update anything that goes wrong on the main fx ctx.

                            holdHighlighting = true;
                            compilerOutputDisplay.appendText(e.toString());
                            if (e.getLocation() != null) {
                                compilerOutputDisplay.appendText("\n" + e.getLocation());
                                if (e.getLocation().getStartIndex() > 0 && e.getLocation().getStartIndex() < userCodeInput.getText().length())
                                    userCodeInput.setStyleClass(e.getLocation().getStartIndex(), e.getLocation().getEndIndex(), "issue");
                            }
                        });
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }

                    Platform.runLater(() -> {
                        compileButton.setText("Compile");
                    });


                });  // End of buildThead

                buildThread.setDaemon(true);
                buildThread.start();

                compileButton.setText("Stop Build");

            }

        }
    }


    //helpers for ModelView

    /**
     * This recieves a list of all valid models and registers them with the combobox
     *
     * @param models a collection of the processIDs of all valid models
     */
    private void updateModelsList(Collection<String> models) {

        //New Tab
        modelsListNew.getItems().clear();
        models.forEach(modelsListNew.getItems()::add);
        modelsListNew.getSelectionModel().selectFirst();
    }

    /*
       Display results  of operations and equations!
     */
    private void updateLogText(List<OperationResult> opRes, List<OperationResult> eqRes) {
        if (opRes.size() > 0)
            compilerOutputDisplay.appendText("\n##Operation Results##\n");

        opRes.forEach(o -> compilerOutputDisplay.appendText(o.getOp().myString() + " = " + o.getResult() + "  " + o.getExtra() + "\n"));


        if (eqRes.size() > 0) {
            compilerOutputDisplay.appendText("\n##Equation Results##\n");

            for (OperationResult result : eqRes) {
                //compilerOutputDisplay.appendText(result.getProcess1().getIdent() + " " + result.getOperation() + " " +
                //  result.getProcess2().getIdent() + " = " + result.getResult() + "\n");
                compilerOutputDisplay.appendText(result.getOp().myString() + "\n");


                if (result.getFailures().size() > 0) {
                    compilerOutputDisplay.appendText("\tFailing Combinations:  ");

                    for (String failure : result.getFailures())
                        compilerOutputDisplay.appendText(failure + "\n");
                }

                compilerOutputDisplay.appendText("\tSimulations passed: " + result.getExtra() + "\n");

            }
        }

    }

    private void addRecentFile(String filePath) {
        if (!recentFilePaths.contains(filePath)) {
            while (recentFilePaths.size() > 5) { // Incase someone adds a shit ton of entries into the settings file
                recentFilePaths.pollLast();
            }


            recentFilePaths.offerFirst(filePath);


            openRecentTab.getItems().clear();

            for (String path : recentFilePaths) {
                MenuItem newItem = new MenuItem(path);
                newItem.setOnAction(e -> {
                    openFile(path);
                });
                openRecentTab.getItems().add(newItem);
            }
        }

    }


    public ArrayDeque<String> getRecentFilePaths() {
        return this.recentFilePaths;
    }


    public void handlePetriIdentification(ActionEvent actionEvent) {

        visualPetriToProcessCodeHelper = new VisualPetriToProcessCodeHelper();

        String conversionResult = visualPetriToProcessCodeHelper.doConversion(ModelView.getInstance().getVisualCreatedPetris(),
            ModelView.getInstance().getOwnersToPIDMapping());

        System.out.println(conversionResult);

        Alert a = new Alert(Alert.AlertType.NONE);
        a.setAlertType(Alert.AlertType.INFORMATION);

        a.setContentText(conversionResult);

        a.show();

    }

    public String doParelelProcessSpecifying(ArrayList<String> processes, String id) {




        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clientres/ChooseProcess.fxml"));
        loader.setController(chooseProcessController); //links to  SettingsController.java
        String toReturn = "";
        try {
            Stage newProcessChooseStage = new Stage();
            newProcessChooseStage.setTitle("Choose Process");

            Scene windowScene = new Scene(loader.load(), 240, 120);
            newProcessChooseStage.setScene(windowScene);

            //settingsController.setWindow(newProcessStage.getScene().getWindow());
            newProcessChooseStage.initOwner(UserInterfaceApplication.getPrimaryStage());
            //settingsStage.initModality(Modality.APPLICATION_MODAL);
            newProcessChooseStage.initModality(Modality.NONE);
            newProcessChooseStage.setResizable(false);
            chooseProcessController.setCombo(processes);
            newProcessChooseStage.showAndWait();
            toReturn = chooseProcessController.getProcessValue();

        } catch (IOException e) {
            Alert optionsLayoutLoadFailed = new Alert(Alert.AlertType.ERROR);
            optionsLayoutLoadFailed.setTitle("Error encountered when loading new proccess dialogue");
            optionsLayoutLoadFailed.setContentText("Error: " + e.getMessage());

            optionsLayoutLoadFailed.initModality(Modality.APPLICATION_MODAL);

            optionsLayoutLoadFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            optionsLayoutLoadFailed.show();
        }

        System.out.println("Returing");
        return toReturn;
    }

    public void handleCreateToggle(ActionEvent actionEvent) {
        ModelView.getInstance().switchInteractionType("create");
    }

    public void handleTokenToggle(ActionEvent actionEvent) {
        ModelView.getInstance().switchInteractionType("token");
    }

    public void handleAutoPetriRelationToggle(ActionEvent actionEvent) {
        ModelView.getInstance().switchInteractionType("autopetrirelation");
    }

    public String nameParrelelProceses() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/clientres/NameParrelelProcess.fxml"));
        loader.setController(nameNewParrelelProcessController); //links to  SettingsController.java
        String toReturn = "";
        try {
            Stage nameParrelelProcesesStage = new Stage();
            nameParrelelProcesesStage.setTitle("Label Parrelel Processes");

            Scene windowScene = new Scene(loader.load(), 402, 326);
            nameParrelelProcesesStage.setScene(windowScene);

            //settingsController.setWindow(newProcessStage.getScene().getWindow());
            nameParrelelProcesesStage.initOwner(UserInterfaceApplication.getPrimaryStage());
            //settingsStage.initModality(Modality.APPLICATION_MODAL);
            nameParrelelProcesesStage.initModality(Modality.NONE);
            nameParrelelProcesesStage.setResizable(false);
            nameParrelelProcesesStage.showAndWait();
            toReturn = nameNewParrelelProcessController.getNewProcessNameValue();

        } catch (IOException e) {
            Alert optionsLayoutLoadFailed = new Alert(Alert.AlertType.ERROR);
            optionsLayoutLoadFailed.setTitle("Error encountered when loading new proccess dialogue");
            optionsLayoutLoadFailed.setContentText("Error: " + e.getMessage());

            optionsLayoutLoadFailed.initModality(Modality.APPLICATION_MODAL);
            optionsLayoutLoadFailed.initOwner(modelDisplayNew.getScene().getWindow());

            optionsLayoutLoadFailed.getButtonTypes().setAll(new ButtonType("Okay", ButtonBar.ButtonData.CANCEL_CLOSE));
            optionsLayoutLoadFailed.show();
        }


        System.out.println("returning: " + toReturn);

        return toReturn;
    }

    public void changeSyncingEdgeWeight(double sync) {
        ModelView.getInstance().setSyningEdgeWight(sync);
    }

    public void changeRelatingEdgeWeight(double relatingWeight) {
        ModelView.getInstance().setRelatingEdgeWight(relatingWeight);
    }
}
