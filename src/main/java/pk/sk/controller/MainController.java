package pk.sk.controller;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import pk.sk.model.Individual;
import pk.sk.model.IndividualType;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainController implements Initializable {
    private static final int WIDTH = 100;
    private static final int HEIGHT = 100;

    private static final int DEFAULT_PIXEL = 0x00000000; // transparent black
    private static final int COOPERATORS_PIXEL = 0xFFFFFFFF; // white
    private static final int DEFECTORS_PIXEL = 0xFF000000; // black
    private static final int LEADERS_RANGE = 3;
    private static final int INDIVIDUAL_RANGE = 2;
    private static final int GROUP_MARK_RANGE = 1;

    private static boolean isRunning = false;

    public TextField initialPopulationField;
    public TextField defectorsField;
    public TextField maxPopulationPerGroup;
    public TextField maxNumberOfGroupsField;
    public Button runButton;
    public TextField delayField;
    public TextField probabilityOfSplittingGroupField;
    public Label statusBar;

    @FXML
    private ImageView outputContainer;
    private BufferedImage outputImage;
    private List<Optional<Individual>> individuals = new ArrayList<>();
    private int[] pixels = new int[]{};
    private int width;
    private int height;
    private Random random = new Random();
    private HashMap<Integer, Integer> colorsOfGroup = new HashMap<>();
    private long cycle;
    private int lastGroupNumber;

    public static void quit() {
        isRunning = false;
    }

    public void createOutputImage() {
        outputImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_4BYTE_ABGR);

        width = outputImage.getWidth();
        height = outputImage.getHeight();
        pixels = outputImage.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);

        refreshImage();
        updateStatusBar();
    }

    private void cleanUp() {
        cycle = 0;
        lastGroupNumber = 0;
        individuals.clear();
        individuals.addAll(Collections.nCopies(HEIGHT * WIDTH, Optional.empty()));
    }

    private void setupOutputContainer() {
        outputContainer.setPreserveRatio(true);
        outputContainer.setFitHeight(500);
        outputContainer.setSmooth(true);
    }

    private void generateRandomGroups() {
        int maxNumberOfGroups = Integer.parseInt(maxNumberOfGroupsField.getText());
        int min = Math.max(maxNumberOfGroups / 2, 3);
        int numberOfGroups = min + random.nextInt(maxNumberOfGroups - min + 1);

        generateRandomGroupLeaders(numberOfGroups);
    }

    private void generateRandomGroupLeaders(int numberOfGroups) {
        for (int group = 0; group < numberOfGroups; group++) {
            int position = random.nextInt(WIDTH * HEIGHT);

            if (individuals.get(position).isPresent()
                    || !getNearestNeighboursIn(position, LEADERS_RANGE).isEmpty()) {
                group--;
            } else {
                Individual newIndividual = new Individual(lastGroupNumber++, position);
                individuals.set(position, Optional.of(newIndividual));
                colorsOfGroup.put(group, getRandomColor());
            }
        }
    }

    private void generateRandomPopulation() {
        int defectors = Integer.parseInt(defectorsField.getText()) * getInitialPopulation() / 100;
        long population = getInitialPopulation() - countGroups();

        generateRandomCooperators(population);
        chooseRandomDefectors(defectors);
    }

    private void chooseRandomDefectors(int defectors) {
        List<Individual> individualList = getIndividualsList();
        for (int i = 0; i < defectors; i++) {
            int position = random.nextInt(individualList.size());
            Individual randomIndividual = individualList.get(position);

            if (IndividualType.COOPERATOR.equals(randomIndividual.getType())) {
                randomIndividual.setType(IndividualType.DEFECTOR);
            } else {
                i--;
            }
        }
    }

    private List<Individual> getIndividualsList() {
        return individuals.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private long countGroups() {
        return getDistinctGroup().count();
    }

    private Stream<Integer> getDistinctGroup() {
        return individuals.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Individual::getGroup)
                .distinct();
    }

    private void generateRandomCooperators(long population) {
        int length = WIDTH * HEIGHT;
        for (int i = 0; i < population; i++) {
            int position = random.nextInt(length);
            if (individuals.get(position).isPresent()) {
                i--;
                continue;
            }
            List<Individual> neighbours = getNearestNeighboursIn(position, INDIVIDUAL_RANGE);
            Set<Integer> neighboursGroup = neighbours.stream()
                    .map(Individual::getGroup)
                    .collect(Collectors.toSet());
            if (neighboursGroup.size() != 1
                    || getGroupSize(neighbours.get(0).getGroup()) >= getMaxPopulationPerGroup()) {
                i--;
                continue;
            }
            individuals.set(position, Optional.of(new Individual(neighbours.get(0).getGroup(), position)));
        }
    }

    private long getGroupSize(int groupNo) {
        return getIndividualsList().stream()
                .filter(individual -> individual.getGroup() == groupNo)
                .count();
    }

    private List<Individual> getNearestNeighboursIn(int position, int range) {
        List<Individual> neighbours = new ArrayList<>();
        getNeighboursPosition(position, range)
                .forEach(coordinates -> {
                    individuals.get(coordinates).ifPresent(neighbours::add);
                });
        return neighbours;
    }

    private int getInitialPopulation() {
        return Integer.parseInt(initialPopulationField.getText()) * getMaxPopulation() / 100;
    }

    private int getRandomColor() {
        int alpha = 0xFF;
        int range = 160;
        int minimumValue = (255 - range) / 5 * 4;

        int red = random.nextInt(range) + minimumValue;
        int green = random.nextInt(range) + minimumValue;
        int blue = random.nextInt(range) + minimumValue;

        return (blue) | (green << 8) | (red << 16) | (alpha << 24);
    }

    private int getMaxPopulation() {
        return Integer.parseInt(maxNumberOfGroupsField.getText()) * getMaxPopulationPerGroup();
    }

    private int getMaxPopulationPerGroup() {
        return Integer.parseInt(maxPopulationPerGroup.getText());
    }

    private int getChanceToSplitting() {
        return Integer.parseInt(probabilityOfSplittingGroupField.getText());
    }

    private void markGroupArea(int index) {
        int group = individuals.get(index).get().getGroup();
        int color = colorsOfGroup.get(group);

        getNeighboursPosition(index, GROUP_MARK_RANGE)
                .forEach(position -> {
                    if (!individuals.get(position).isPresent()) {
                        pixels[position] = color;
                    }
                });
    }

    private List<Integer> getNeighboursPosition(int index, int range) {
        List<Integer> positionList = new ArrayList<>();
        int xCord = index % width;
        int yCord = index / height;

        for (int i = -range; i <= range; i++) {
            for (int j = -range; j <= range; j++) {

                int y = yCord + i;
                int x = xCord + j;
                int position = y * width + x;
                if (y < 0 || x < 0 || y >= height || x >= width
                        || position == index || position < 0 || position > WIDTH * HEIGHT) {
                    continue;
                }
                positionList.add(position);
            }
        }
        return positionList;
    }

    private void refreshImage() {
        updatePixelValues();
        outputImage.setRGB(0, 0, width, height, pixels, 0, width);
        outputContainer.setImage(SwingFXUtils.toFXImage(outputImage, null));
    }

    private void updatePixelValues() {
        markIndividuals();
        markGroups();
    }

    private void markGroups() {
        int length = WIDTH * HEIGHT;
        for (int i = 0; i < length; i++) {
            int finalI = i;
            individuals.get(i).ifPresent(ind -> markGroupArea(finalI));
        }
    }

    private void markIndividuals() {
        int length = WIDTH * HEIGHT;
        for (int i = 0; i < length; i++) {
            if (individuals.get(i).isPresent()) {
                IndividualType type = individuals.get(i).get().getType();
                if (IndividualType.COOPERATOR.equals(type)) {
                    pixels[i] = COOPERATORS_PIXEL;
                } else {
                    pixels[i] = DEFECTORS_PIXEL;
                }
            } else {
                pixels[i] = DEFAULT_PIXEL;
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupOutputContainer();
        createFieldListeners();
        cleanUp();
    }

    private void createFieldListeners() {
        initialPopulationField.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        initialPopulationField.setText(filterNumber(newValue)));
        defectorsField.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        defectorsField.setText(filterNumber(newValue)));
        delayField.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        delayField.setText(filterNumber(newValue)));
        maxNumberOfGroupsField.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        maxNumberOfGroupsField.setText(filterNumber(newValue)));
        maxPopulationPerGroup.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        maxPopulationPerGroup.setText(filterNumber(newValue)));
        probabilityOfSplittingGroupField.textProperty()
                .addListener((observableValue, oldValue, newValue) ->
                        probabilityOfSplittingGroupField.setText(filterNumber(newValue)));
    }

    private String filterNumber(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                stringBuilder.append(c);
            }
        }
        long number = 0L;
        try {
            number = Long.parseLong(stringBuilder.toString());
        } catch (NumberFormatException ignored) {
        }

        if (number >= 0)
            return number + "";
        return "";
    }

    public void run() {
        if (!isInputValid()) {
            return;
        }
        if (isRunning) {
            runButton.setText("Reset & Run");
            isRunning = false;
        } else {
            runButton.setText("Stop");
            isRunning = true;
            launchSimulation();
        }
    }

    private void launchSimulation() {
        cleanUp();
        generateRandomGroups();
        generateRandomPopulation();
        refreshImage();
        updateStatusBar();
        startAnimation();
    }

    private void startAnimation() {
        new Thread(() -> {
            while (isRunning) {
                cycle++;
                nextStep();
                refreshImage();
                updateStatusBar();
                try {
                    int sleepTime = Integer.parseInt(delayField.getText());
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void updateStatusBar() {
        long cooperators = countIndividuals(IndividualType.COOPERATOR);
        long defectors = countIndividuals(IndividualType.DEFECTOR);
        long total = cooperators + defectors;
        long groups = countGroups();

        String statusMessage = String.format(
                "Total Population: %-5d Cooperators: %-5d Defectors: %-5d Groups: %-5d Cycle: %d",
                total, cooperators, defectors, groups, cycle);

        System.out.println(statusMessage); //todo rem
        Platform.runLater(() -> statusBar.setText(statusMessage));
    }

    private long countIndividuals(IndividualType cooperator) {
        return individuals.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Individual::getType)
                .filter(type -> type.equals(cooperator))
                .count();
    }

    private void nextStep() {
        //TODO
        checkSizeOfGroups();
        System.out.println("next step");
    }

    private void checkSizeOfGroups() {
        List<Integer> groupsToSplitting = new ArrayList<>();
        List<Individual> individualsList = getIndividualsList();
        List<Integer> groupList = individualsList.stream()
                .map(Individual::getGroup)
                .distinct()
                .collect(Collectors.toList());
        groupList.forEach(groupNo -> {
            long size = individualsList.stream()
                    .filter(individual -> individual.getGroup() == groupNo).count();
            if (size >= getMaxPopulationPerGroup()) {
                groupsToSplitting.add(groupNo);
            }
        });
        if (!groupsToSplitting.isEmpty()) {
            groupsToSplitting.forEach(this::tryToSplitGroup);
        }
    }

    private void tryToSplitGroup(Integer groupNo) {
        //todo check probability, split group and kill other or kill individuls
    }

    private boolean isInputValid() {
        //todo add constraint for values
        String errorMessage = "";

        if (initialPopulationField.getText() == null || initialPopulationField.getText().length() == 0) {
            errorMessage += "Empty field: 'Initial population'!\n";
        }
        if (defectorsField.getText() == null || defectorsField.getText().length() == 0) {
            errorMessage += "Empty field: 'Defectors'!\n";
        }
        if (maxNumberOfGroupsField.getText() == null || maxNumberOfGroupsField.getText().length() == 0) {
            errorMessage += "Empty field: 'Max number of groups'!\n";
        }
        if (maxPopulationPerGroup.getText() == null || maxPopulationPerGroup.getText().length() == 0) {
            errorMessage += "Empty field: 'Max population per group'!\n";
        }

        if (delayField.getText() == null || delayField.getText().length() == 0) {
            errorMessage += "Empty field: 'Delay [ms]'!\n";
        }
        if (probabilityOfSplittingGroupField.getText() == null || probabilityOfSplittingGroupField.getText().length() == 0) {
            errorMessage += "Empty field: 'Probability of split the group [‰]'!\n";
        }
        if (errorMessage.length() == 0) {
            return true;
        } else {
            alertError(errorMessage);
            return false;
        }
    }

    private void alertError(String finalErrorMessage) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(runButton.getScene().getWindow());
            alert.setTitle("Invalid Fields");
            alert.setHeaderText("");
            alert.setContentText(finalErrorMessage);
            alert.showAndWait();
        });
    }
}
