package xyz.jordanplayz158.vpkextractor.gui;

import com.connorhaigh.javavpk.core.Archive;
import com.connorhaigh.javavpk.core.Directory;
import com.connorhaigh.javavpk.core.Entry;
import com.connorhaigh.javavpk.exceptions.ArchiveException;
import com.connorhaigh.javavpk.exceptions.EntryException;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

public class MainController {
    @FXML
    private TreeView tree;

    @FXML
    private TextArea fileInfo;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private ProgressIndicator progressIndicator;

    private FileChooser fileChooser;

    private Archive archive;

    private TreeItem lastItem = null;

    @FXML
    private void initialize() {
        FileChooser fileChooserVpk = new FileChooser();
        fileChooserVpk.setTitle("Choose VPK File");
        fileChooserVpk.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("VPK", "*.vpk"));

        fileChooser = fileChooserVpk;

        ContextMenu treeContextMenu = new ContextMenu();
        MenuItem extractMenuItem = new MenuItem();
        extractMenuItem.setText("Extract Selected");
        extractMenuItem.setOnAction(event -> {
            ObservableList<TreeItem> items = tree.getSelectionModel().getSelectedItems();

            String[] paths = new String[items.size()];

            for (int i = 0; i < items.size(); i++) {
                paths[i] = (String) items.get(i).getValue();
            }

            extract(paths);
        });

        treeContextMenu.getItems().add(extractMenuItem);

        tree.setContextMenu(treeContextMenu);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    @FXML
    public void onFileOpenClick() {
        try {
            File file = fileChooser.showOpenDialog(GUI.stage);

            if(file == null) {
                return;
            }

            Archive archive = new Archive(file);
            archive.load();

            TreeItem<?> baseTreeItem = new TreeItem<>(archive.getFile().getName());

            for (Directory directory : archive.getDirectories()) {

                // Get the directory path, split by `/` and iterate over the entries
                // For each entry, iterate over baseTreeItem and see if TreeItem is
                // in children, if not create, store it and go to next one and
                // check if the next one IS IN the previous TreeItem

                TreeItem<?> lastTreeItem = null;

                String[] subDirectories = directory.getPath().split("/");

                if(subDirectories.length == 1 && subDirectories[0].length() == 0) {
                    for (Entry entry : directory.getEntries()) {
                        baseTreeItem.getChildren().add(new TreeItem(entry.getFullName()));
                    }
                } else {
                    subDirectoryFor:
                    for (String subDirectory : subDirectories) {
                        TreeItem<?> treeItem = lastTreeItem == null ? baseTreeItem : lastTreeItem;

                        for (Object child : treeItem.getChildren()) {
                            if (child instanceof TreeItem<?> treeItemChild && treeItemChild.getValue().equals(subDirectory)) {
                                lastTreeItem = treeItemChild;
                                continue subDirectoryFor;
                            }
                        }

                        TreeItem currentTreeItem = new TreeItem(subDirectory);
                        treeItem.getChildren().add(currentTreeItem);
                        lastTreeItem = currentTreeItem;
                    }

                    for (Entry entry : directory.getEntries()) {
                        lastTreeItem.getChildren().add(new TreeItem(entry.getFullName()));
                    }
                }
            }

            // Sort by file type (file or folder), then by a-Z
            baseTreeItem = recursionSort(baseTreeItem);

            tree.setRoot(baseTreeItem);

            this.archive = archive;
        } catch (ArchiveException | IOException | EntryException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void onTreeNavigation(InputEvent event) {
        if(event instanceof KeyEvent keyEvent) {
            if(keyEvent.getCode().isArrowKey()) {
                updateFileInfoPane();
            }

            return;
        }

        if(event instanceof MouseEvent) {
            updateFileInfoPane();
        }
    }

    private void updateFileInfoPane() {
        TreeItem selectedItem = ((TreeItem) tree.getSelectionModel().getSelectedItem());
        int currentIndex = tree.getSelectionModel().getSelectedIndex();

        if(selectedItem == lastItem) {
            return;
        }

        lastItem = selectedItem;

        if(currentIndex == 0) {
            fileInfo.setText(String.format("""
                    Header Length: %s
                    Tree Length: %s
                    Signature: %s
                    Version: %s
                    MultiPart? %s
                    """, archive.getHeaderLength(), archive.getTreeLength(), archive.getSignature(), archive.getVersion(), archive.isMultiPart()));

            return;
        }

        if(selectedItem == null) {
            fileInfo.setText("");
            return;
        }

        if(selectedItem.getChildren().size() == 0) {
            String fileName = (String) selectedItem.getValue();
            StringBuilder filePathStringBuilder = new StringBuilder(fileName);

            TreeItem lastParent = selectedItem.getParent();

            while(lastParent != null && !((String) lastParent.getValue()).equals(archive.getFile().getName())) {
                filePathStringBuilder.insert(0, (String) lastParent.getValue() + "/");

                lastParent = lastParent.getParent();
            }

            String filePath = filePathStringBuilder.toString();

            fileInfo.setText(new String(readFileFromArchive(filePath)));
        } else {
            fileInfo.setText("");
        }
    }

    private TreeItem recursionSort(TreeItem treeItem) {
        treeItem.getChildren().sort((Comparator<TreeItem<?>>) (o1, o2) -> {
            Integer children1 = o1.getChildren().size() > 0 ? 1 : 0;
            Integer children2 = o2.getChildren().size() > 0 ? 1 : 0;
            int childrenComp = children2.compareTo(children1);

            if(childrenComp != 0) {
                return childrenComp;
            }

            String name1 = (String) o1.getValue();
            String name2 = (String) o2.getValue();
            return name1.compareTo(name2);
        });

        treeItem.getChildren().replaceAll(o -> recursionSort((TreeItem) o));

        return treeItem;
    }

    public byte[] readFileFromArchive(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);

        for (Directory directory : archive.getDirectories()) {
            if(!filePath.startsWith(directory.getPath())) continue;

            for (Entry entry : directory.getEntries()) {
                if(entry.getFullName().equals(fileName)) {
                    try {
                        return entry.readData();
                    } catch (IOException | ArchiveException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return null;
    }

    public void extract(String[] paths) {
        if(archive == null) {
            return;
        }

        String archiveName = archive.getFile().getName();
        String archiveFolder = archiveName.substring(0, archiveName.lastIndexOf("."));
        int archiveDirectories = archive.getDirectories().size();

        if(paths == null) {
            for (int i = 0; i < archiveDirectories; i++) {
                Directory directory = archive.getDirectories().get(i);

                for (Entry entry : directory.getEntries()) {
                    try {
                        String parentDirectory = archiveFolder + "/" + directory.getPath();
                        String fileName = entry.getFullName();

                        File parentDirectoryFile = new File(parentDirectory);

                        if(parentDirectoryFile.exists() || parentDirectoryFile.mkdirs()) {
                            entry.extract(new File(parentDirectoryFile, fileName));
                        }
                    } catch (IOException | ArchiveException e) {
                        throw new RuntimeException(e);
                    }
                }

                double progress = i + 1.0 / archiveDirectories;
                progressIndicator.setProgress(progress);
                progressBar.setProgress(progress);
            }

            progressIndicator.setProgress(0.0);
            progressBar.setProgress(0.0);
            return;
        }



        for (int i = 0; i < archiveDirectories; i++) {
            Directory directory = archive.getDirectories().get(i);

            boolean directoryStartsWithApprovedPath = false;
            for (String path : paths) {
                if(directory.getPath().startsWith(path)) {
                    directoryStartsWithApprovedPath = true;
                    break;
                }
            }

            if(!directoryStartsWithApprovedPath) continue;

            for (Entry entry : directory.getEntries()) {
                try {
                    String parentDirectory = archiveFolder + "/" + directory.getPath();
                    String fileName = entry.getFullName();

                    File parentDirectoryFile = new File(parentDirectory);

                    if(parentDirectoryFile.exists() || parentDirectoryFile.mkdirs()) {
                        entry.extract(new File(parentDirectoryFile, fileName));
                    }
                } catch (IOException | ArchiveException e) {
                    throw new RuntimeException(e);
                }
            }

            double progress = i + 1.0 / paths.length;
            progressIndicator.setProgress(progress);
            progressBar.setProgress(progress);
        }

        progressIndicator.setProgress(0.0);
        progressBar.setProgress(0.0);
        return;

    }

    @FXML
    public void onWholeExtractButtonClick() {
        extract(null);
    }
}
