package org.example;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.example.config.AppConfig;
import org.example.models.FileInfo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class DownloadManager {
    @FXML
    private TableView<FileInfo> tableView;
    @FXML
    private TextField urlTextField;
    private ExecutorService executorService;
    private LinkedBlockingQueue<FileInfo> fileQueue;

    // which is used to store FileInfo objects representing files to be downloaded.
    private int filesDownloaded;
    //The filesDownloaded field keeps track of the number of files that have been downloaded.

    @FXML
        //It extracts the URL, filename, status, action, and path from the UI elements,
        // creates a new FileInfo object, adds it to the table view,
        // and offers it to the fileQueue for processing.
    void downloadButtonClicked(ActionEvent event) {
        String url = this.urlTextField.getText().trim();
        String filename = url.substring(url.lastIndexOf("/") + 1);
        String status = "WAITING";
        String action = "OPEN";
        String path = AppConfig.DOWNLOAD_PATH + File.separator + filename;
        FileInfo file = new FileInfo((filesDownloaded + 1) + "", filename, url, status, action, path, "0");
        filesDownloaded++;
        this.tableView.getItems().add(Integer.parseInt(file.getIndex()) - 1, file);
        fileQueue.offer(file);
        this.urlTextField.setText("");
    }
    //The updateUI() method updates the UI elements with the status and progress of a file download.
    // It is invoked on the JavaFX application thread using Platform.runLater()
    // to ensure thread safety.
    public void updateUI(FileInfo metaFile) {
        Platform.runLater(() -> {
            FileInfo fileInfo = this.tableView.getItems().get(Integer.parseInt(metaFile.getIndex()) - 1);
            fileInfo.setStatus(metaFile.getStatus());
            DecimalFormat decimalFormat = new DecimalFormat("0.00");
            fileInfo.setPer(decimalFormat.format(Double.parseDouble(metaFile.getPer())));
            this.tableView.refresh();
        });
    }
    //The initialize() method is an initialization method for the JavaFX controller.
    // It configures the cell value factories for each column in the table view,
    // initializes the fileQueue and executorService,
    // and starts the download tasks by executing a loop five times.
    @FXML
    public void initialize() {
        TableColumn<FileInfo, String> sn = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(0);
        sn.setCellValueFactory(p -> p.getValue().indexProperty());

        TableColumn<FileInfo, String> filename = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(1);
        filename.setCellValueFactory(p -> p.getValue().nameProperty());

        TableColumn<FileInfo, String> url = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(2);
        url.setCellValueFactory(p -> p.getValue().urlProperty());

        TableColumn<FileInfo, String> status = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(3);
        status.setCellValueFactory(p -> p.getValue().statusProperty());

        TableColumn<FileInfo, String> per = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(4);
        per.setCellValueFactory(p -> {
            SimpleStringProperty simpleStringProperty = new SimpleStringProperty();
            simpleStringProperty.set(p.getValue().getPer() + "%");
            return simpleStringProperty;
        });

        TableColumn<FileInfo, String> action = (TableColumn<FileInfo, String>) this.tableView.getColumns().get(5);
        action.setCellValueFactory(p -> p.getValue().actionProperty());

        fileQueue = new LinkedBlockingQueue<>();
        executorService = Executors.newFixedThreadPool(5);

        //Inside the loop, a lambda expression is executed on a thread from the executorService.
        // It waits for a file to become available in the fileQueue,
        // updates the file status in the UI,
        // and invokes the downloadFile() method to perform the actual download.
        for (int i = 0; i < 5; i++) {
            executorService.execute(() -> {
                try {
                    while (true) {
                        FileInfo file = fileQueue.take(); // Wait for a file to become available
                        file.setStatus("Downloading");
                        updateUI(file);
                        downloadFile(file);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void downloadFile(FileInfo file) {
        try {
            //DownloadLogic
            URL url = new URL(file.getUrl());
            URLConnection urlConnection = url.openConnection();

            int fileSize = urlConnection.getContentLength();
            int countByte = 0;
            double per = 0.0;
            double byteSum = 0.0;
            BufferedInputStream bufferedInputStream = new BufferedInputStream(url.openStream());

            FileOutputStream fos = new FileOutputStream(file.getPath());
            byte data[] = new byte[1024];
            while (true) {
                countByte = bufferedInputStream.read(data, 0, 1024);
                if (countByte == -1) {
                    break;
                }
                fos.write(data, 0, countByte);
                byteSum = byteSum + countByte;
                if (fileSize > 0) {
                    per = (byteSum / fileSize * 100);
                    file.setPer(per + "");
                    updateUI(file);
                }
            }

            fos.close();
            bufferedInputStream.close();

            file.setStatus("DONE");
        } catch (IOException e) {
            file.setStatus("FAILED");
            System.out.println("Downloading error");
            e.printStackTrace();
        } finally {
            updateUI(file);
        }
    }
}
