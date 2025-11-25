import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea; // Add for system remind for recorder left and annocement
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser; // Add for export the file
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.util.Duration; // Add for recording time management
// Add for animation
import javafx.scene.control.Button;
import javafx.animation.Timeline;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.io.IOException;
import java.util.LinkedList;

public class MainWindow {
    @FXML
    ChoiceBox<String> chbMode;

    @FXML
    Canvas canvas;

    @FXML
    Pane container;

    @FXML
    Pane panePicker;

    @FXML
    Pane paneColor;

    @FXML
    Button StartRecordBtn;
    @FXML
    Button PauseRecordBtn;
    @FXML
    Button PlayAnimBtn;
    @FXML
    Button ExportGifBtn;


    client client;
    String username;
    int numPixels = 50;
    Stage stage;
    AnimationTimer animationTimer;
    int[][] data;
    double pixelSize, padSize, startX, startY;
    int selectedColorARGB;
    boolean isPenMode = true;
    LinkedList<Point> filledPixels = new LinkedList<Point>();

    // Animation Variable
    private ArrayList<BufferedImage> animationSteps = new ArrayList<>();
    private Timeline catchUpTimeline;
    private int currentStep = 0;
    private boolean isRecording = false;
    private String recorderName = "";
    private boolean isPlaying = false;

    class Point{
        int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public MainWindow(Stage stage, String username, client client) throws IOException {
        this.username = username;
        this.client = client;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainWindownUI.fxml"));
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        this.stage = stage;

        stage.setScene(scene);
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        canvas.widthProperty().bind(container.widthProperty());
        canvas.heightProperty().bind(container.heightProperty());
        canvas.widthProperty().addListener(w->onCanvasSizeChange());
        canvas.heightProperty().addListener(h->onCanvasSizeChange());

        stage.setOnCloseRequest(event -> quit());

        stage.show();
        initial();

        animationTimer.start();

        // Initialize animation timer.
        catchUpTimeline = new Timeline(new KeyFrame(Duration.millis(400), e -> playNextStep()));
        catchUpTimeline.setCycleCount(Timeline.INDEFINITE);

        AnimationButtons();
    }
    // Manage animation button actions
    void AnimationButtons(){
        StartRecordBtn.setOnAction((e-> startRecording()));
        PauseRecordBtn.setOnAction(e-> pauseRecording());
        PlayAnimBtn.setOnAction(e-> startPlaying());
        ExportGifBtn.setOnAction(e-> exportGif());
    }

    // Start recording -> send request to server
    void startRecording(){
        try{
            DataOutputStream out = new DataOutputStream(client.serverSocket.getOutputStream());
            out.writeInt(401); // Action code for start recording
            //out.writeUTF("START_RECORDING " + username);
            out.writeInt(username.length());
            out.writeBytes(username);
            out.flush();
            isRecording = true;
            recorderName = username;
            updateButtonStates();
            //addSystemMsg("Recording started.\n");
            BufferedImage initialFrame = new BufferedImage(numPixels, numPixels, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < numPixels; y++) {
                for (int x = 0; x < numPixels; x++) {
                    initialFrame.setRGB(x, y, data[y][x]); // Copy original pixel data
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(initialFrame, "png", baos);
            out.writeInt(405); // Action code: Send frame
            out.writeInt(baos.size());
            out.write(baos.toByteArray());
            out.flush();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    void pauseRecording() {
        try {
            DataOutputStream out = new DataOutputStream(client.serverSocket.getOutputStream());
            out.writeInt(402); // Action code for pause recording
            //out.writeUTF("PAUSE_RECORDING " + username);
            out.flush();
            isRecording = false;
            updateButtonStates();
            //addSystemMsg("Recording paused.\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    void startPlaying() {
        if (!isPlaying) {
            try {
                DataOutputStream out = new DataOutputStream(client.serverSocket.getOutputStream());
                out.writeInt(404); // Action code for play animation
                //out.writeUTF("PLAY_ANIMATION ");
                out.flush();
                isPlaying = true;
                disableInputs();
                updateButtonStates();
                //addSystemMsg("Playing animation...\n");
                requestAnimationFrames();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    void exportGif() {
        if (isRecording || isPlaying || !recorderName.equals(username)) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Animation as GIF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GIF files (*.gif)", "*.gif"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                DataOutputStream out = new DataOutputStream(client.serverSocket.getOutputStream());
                out.writeInt(406); // Action code for export GIF
                //out.writeUTF("EXPORT_GIF " + file.getAbsolutePath());
                out.writeInt(file.getAbsolutePath().length());
                out.writeBytes(file.getAbsolutePath());
                out.flush();
                //addSystemMsg("Exporting GIF");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void requestAnimationFrames(){
        new Thread(()->{
            try{
                DataInputStream in = new DataInputStream(client.serverSocket.getInputStream());
                int frameCount = in.readInt();
                System.out.println("Server says there are " + frameCount + " frames");

                animationSteps.clear();
                for(int i=0; i<frameCount; i++){
                    int length = in.readInt();
                    byte[] imageData = new byte[length];
                    in.readFully(imageData);
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
                    BufferedImage img = ImageIO.read(bais);
                    if (img == null) {
                        System.out.println("Frame " + i + " FAILED to decode!");
                    } else {
                        animationSteps.add(img);
                        System.out.println("Frame " + i + " loaded: " + img.getWidth() + "x" + img.getHeight());
                    }
                }

                currentStep = 0;
                if (animationSteps.isEmpty()) {
                    System.out.println("NO FRAMES LOADED! Cannot play.");
                    isPlaying = false;
                    updateButtonStates();
                    return;
                }

                System.out.println("Starting playback of " + animationSteps.size() + " frames");
                catchUpTimeline.playFromStart();  // <-- VERY IMPORTANT

            }catch (Exception e){
                e.printStackTrace();
            }
        }).start();
    }

    void playNextStep(){
        System.out.println("playNextStep() called, currentStep = " + currentStep + " / " + animationSteps.size());

        if(currentStep >= animationSteps.size()){
            System.out.println("Animation finished.");
            catchUpTimeline.stop();
            isPlaying = false;
            enableInputs();
            updateButtonStates();
            return;
        }

        BufferedImage frame = animationSteps.get(currentStep);

        // Copy pixels
        for (int y = 0; y < numPixels; y++) {
            for (int x = 0; x < numPixels; x++) {
                int rgb = frame.getRGB(x, y);
                data[y][x] = rgb;
            }
        }

        System.out.println("Frame " + currentStep + " applied. Sample pixel (0,0): " + Integer.toHexString(data[0][0]));

        render();        // Force redraw
        currentStep++;
    }
    // Send Canvas data to server
    void sendCanvasData(){
        if (!isRecording) return;
        try {
            BufferedImage frame = new BufferedImage(numPixels, numPixels, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < numPixels; y++) {
                for (int x = 0; x < numPixels; x++) {
                    frame.setRGB(x, y, data[y][x]);
                }
            }

            DataOutputStream out = new DataOutputStream(client.serverSocket.getOutputStream());
            out.writeInt(405); // Add this line: Command code for "Send Frame" (server expects 405)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(frame, "png", baos);

            out.writeInt(baos.size());
            out.write(baos.toByteArray());
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateButtonStates() {
        StartRecordBtn.setDisable(isRecording || isPlaying);
        PauseRecordBtn.setDisable(!isRecording || isPlaying || !recorderName.equals(username));
        PlayAnimBtn.setDisable(isRecording || isPlaying);
        ExportGifBtn.setDisable(isRecording || isPlaying || !recorderName.equals(username));

        StartRecordBtn.setText(isRecording ? "Recording..." : "Start Rec");
        PauseRecordBtn.setText(isRecording ? "Pause Rec" : "Paused");
        PlayAnimBtn.setText(isPlaying ? "Playing..." : "Play Anim");
    }

    private void disableInputs() {
        canvas.setDisable(true);
        chbMode.setDisable(true);
    }

    private void enableInputs() {
        canvas.setDisable(false);
        chbMode.setDisable(false);
    }

//    private void addSystemMsg(String msg) {
//        areaMsg.appendText("[System] " + msg);
//    }


    /**
     * Update canvas info when the window is resized
     */
    void onCanvasSizeChange() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        padSize = Math.min(w, h);
        startX = (w - padSize)/2;
        startY = (h - padSize)/2;
        pixelSize = padSize / numPixels;
    }

    /**
     * terminate this program
     */
    void quit() {
        System.out.println("Bye bye");
        stage.close();
        System.exit(0);
    }

    /**
     * Initialize UI components
     * @throws IOException
     */
    void initial() throws IOException {
        data = new int[numPixels][numPixels];

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                render();
            }
        };

        chbMode.setValue("Pen");

        canvas.setOnMousePressed(event -> {
            isPenMode = chbMode.getValue().equals("Pen");
            filledPixels.clear();
            if (isPenMode)
                penToData(event.getX(), event.getY());
        });

        canvas.setOnMouseDragged(event -> {
            if (isPenMode)
                penToData(event.getX(), event.getY());
        });

        canvas.setOnMouseReleased(event->{
            if (!isPenMode)
                bucketToData(event.getX(), event.getY());
            sendCanvasData();
        });

        initColorMap();
    }

    /**
     * Initialize color map
     * @throws IOException
     */
    void initColorMap() throws IOException {
        Image image = new Image("file:color_map.png");
        ImageView imageView = new ImageView(image);

        imageView.setFitHeight(30.0);
        imageView.setPreserveRatio(true);
        panePicker.getChildren().add(imageView);

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double viewWidth = imageView.getBoundsInParent().getWidth();
        double viewHeight = imageView.getBoundsInParent().getHeight();

        double scaleX = imageWidth / viewWidth;
        double scaleY = imageHeight / viewHeight;

        pickColor(image, 0, 0, imageWidth, imageHeight);

        panePicker.setOnMouseClicked(event -> {
            double x = event.getX();
            double y = event.getY();

            int imgX = (int)(x * scaleX);
            int imgY = (int)(y * scaleY);

            pickColor(image, imgX, imgY, imageWidth, imageHeight);
        });
    }

    /**
     * Pick a color from the color map image
     * @param image color map image
     * @param imgX x position in the image
     * @param imgY y position in the image
     * @param imageWidth the width of the image
     * @param imageHeight the height of the image
     */
    void pickColor(Image image, int imgX, int imgY, double imageWidth, double imageHeight) {
        if (imgX >= 0 && imgX < imageWidth && imgY >= 0 && imgY < imageHeight) {
            PixelReader reader = image.getPixelReader();

            selectedColorARGB = reader.getArgb(imgX, imgY);

            Color color = reader.getColor(imgX, imgY);
            paneColor.setStyle("-fx-background-color:#" + color.toString().substring(2));
        }
    }

    /**
     * Invoked when the Pen mode is used. Update sketch data array and store updated pixels in a list named filledPixels
     * @param mx mouse down/drag position x
     * @param my mouse down/drag position y
     */
    void penToData(double mx, double my) {
        if (mx > startX && mx < startX + padSize && my > startY && my < startY + padSize) {
            int row = (int) ((my - startY) / pixelSize);
            int col = (int) ((mx - startX) / pixelSize);
            data[row][col] = selectedColorARGB;
            filledPixels.add(new Point(col, row));
        }
    }

    /**
     * Invoked when the Bucket mode is used. It calls paintArea() to update sketch data array and store updated pixels in a list named filledPixels
     * @param mx mouse down/drag position x
     * @param my mouse down/drag position y
     */
    void bucketToData(double mx, double my) {
        if (mx > startX && mx < startX + padSize && my > startY && my < startY + padSize) {
            int row = (int) ((my - startY) / pixelSize);
            int col = (int) ((mx - startX) / pixelSize);
            paintArea(col, row);
        }
    }

    /**
     * Update the color of specific area
     * @param col position of the sketch data array
     * @param row position of the sketch data array
     */
    public void paintArea(int col, int row) {
        int oriColor = data[row][col];
        LinkedList<Point> buffer = new LinkedList<Point>();

        if (oriColor != selectedColorARGB) {
            buffer.add(new Point(col, row));

            while(!buffer.isEmpty()) {
                Point p = buffer.removeFirst();
                col = p.x;
                row = p.y;

                if (data[row][col] != oriColor) continue;

                data[row][col] = selectedColorARGB;
                filledPixels.add(p);

                if (col > 0 && data[row][col-1] == oriColor) buffer.add(new Point(col-1, row));
                if (col < data[0].length - 1 && data[row][col+1] == oriColor) buffer.add(new Point(col+1, row));
                if (row > 0 && data[row-1][col] == oriColor) buffer.add(new Point(col, row-1));
                if (row < data.length - 1 && data[row+1][col] == oriColor) buffer.add(new Point(col, row+1));
            }
        }
    }

    /**
     * Convert argb value from int format to JavaFX Color
     * @param argb
     * @return Color
     */
    Color fromARGB(int argb) {
        return Color.rgb(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                ((argb >> 24) & 0xFF) / 255.0
        );
    }


    /**
     * Render the sketch data to the canvas
     */
    void render() {

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double x = startX;
        double y = startY;

        gc.setStroke(Color.GRAY);
        for (int col = 0; col< numPixels; col++) {
            for (int row = 0; row< numPixels; row++) {
                gc.setFill(fromARGB(data[col][row]));
                gc.fillOval(x, y, pixelSize, pixelSize);
                gc.strokeOval(x, y, pixelSize, pixelSize);
                x += pixelSize;
            }
            x = startX;
            y += pixelSize;
        }

    }
}
