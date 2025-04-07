package wallettemplate;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import network.thunder.core.ThunderContext;
import network.thunder.core.communication.ServerObject;
import network.thunder.core.database.DBHandler;
import network.thunder.core.database.InMemoryDBHandler;
import network.thunder.core.etc.Constants;
import network.thunder.core.helper.callback.results.NullResultCommand;
import network.thunder.core.helper.wallet.MockWallet;
import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.GuiUtils;
import wallettemplate.utils.TextFieldValidator;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static wallettemplate.utils.GuiUtils.*;

/**
 * Entry point for starting the Wallet. We create the context here and load the UI.
 * <p>
 * The rest of the functionality is hidden inside the ThunderContext and wired to the buttons.
 */
public class Main extends Application {
    public static final String APP_NAME = "ThunderWallet";

    public static Main instance;
    public static Wallet wallet;

    public static ThunderContext thunderContext;
    public static DBHandler dbHandler = new InMemoryDBHandler();
    public static ServerObject node = new ServerObject();

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;

    public static int CLIENTID = 1;
    public static String REQUEST;

    public static WalletAppKit walletAppKit;

    @Override
    public void start (Stage mainWindow) throws Exception {
        try {
            realStart(mainWindow);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            throw e;
        }
    }

    private void realStart (Stage mainWindow) throws IOException {
        instance = this;
        prepareUI(mainWindow);

        //Initiate the ThunderContext with a ServerObject
        //For now create a new node key every time. Want to read it from disk here once we have a storage engine
        node.init();

        //TODO move somewhere more central..
        if (Constants.USE_MOCK_BLOCKCHAIN) {
            wallet = new MockWallet(Constants.getNetwork());
        } else {
            System.out.println("Setting up wallet and downloading blockheaders. This can take up to two minutes on first startup");
            walletAppKit = new WalletAppKit(Constants.getNetwork(), new File("wallet"), "wallet_" + CLIENTID);
            walletAppKit.startAsync().awaitRunning();
            wallet = walletAppKit.wallet();
            wallet.allowSpendingUnconfirmedTransactions();
            wallet.reset();
            wallet.addEventListener(new WalletEventListener() {
                @Override
                public void onCoinsReceived (Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    System.out.println("wallet = " + wallet);
                    System.out.println("tx = " + tx);
                    System.out.println("prevBalance = " + prevBalance);
                    System.out.println("newBalance = " + newBalance);
                }

                @Override
                public void onCoinsSent (Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    System.out.println("wallet = " + wallet);
                    System.out.println("tx = " + tx);
                    System.out.println("prevBalance = " + prevBalance);
                    System.out.println("newBalance = " + newBalance);
                }

                @Override
                public void onReorganize (Wallet wallet) {

                }

                @Override
                public void onTransactionConfidenceChanged (Wallet wallet, Transaction tx) {

                }

                @Override
                public void onWalletChanged (Wallet wallet) {
                }

                @Override
                public void onScriptsChanged (Wallet wallet, List<Script> scripts, boolean isAddingScripts) {

                }

                @Override
                public void onKeysAdded (List<ECKey> keys) {

                }
            });
            System.out.println(wallet.getKeyChainSeed());
        }
        System.out.println(wallet);
        thunderContext = new ThunderContext(wallet, dbHandler, node);

        mainWindow.show();
        controller.onBitcoinSetup();

        thunderContext.startUp(new NullResultCommand());
    }

    private void prepareUI (Stage mainWindow) throws IOException {
        this.mainWindow = mainWindow;
        // Show the crash dialog for any exceptions that we don't handle and that hit the main loop.
        GuiUtils.handleCrashesOnThisThread();

        // Load the GUI. The MainController class will be automagically created and wired up.
        File file = new File("main.fxml");
        URL location = getClass().getResource("main.fxml");
        FXMLLoader loader = new FXMLLoader(location);
        mainUI = loader.load();
        controller = loader.getController();
        // Configure the window with a StackPane so we can overlay things on top of the main UI, and a
        // NotificationBarPane so we can slide messages and progress bars in from the bottom. Note that
        // ordering of the construction and connection matters here, otherwise we get (harmless) CSS error
        // spew to the logs.
        notificationBar = new NotificationBarPane(mainUI);
        mainWindow.setTitle(APP_NAME);
        uiStack = new StackPane();
        Scene scene = new Scene(uiStack);
        TextFieldValidator.configureScene(scene);   // Add CSS that we need.
        scene.getStylesheets().add(getClass().getResource("wallet.css").toString());
        uiStack.getChildren().add(notificationBar);
        mainWindow.setScene(scene);
    }

    private Node stopClickPane = new Pane();

    public class OverlayUI <T> {
        public Node ui;
        private T controller;

        public OverlayUI (Node ui, T controller) {
            this.ui = ui;
            this.controller = controller;
        }

        public void show () {
            checkGuiThread();
            if (currentOverlay == null) {
                uiStack.getChildren().add(stopClickPane);
                uiStack.getChildren().add(ui);
                blurOut(mainUI);
                //darken(mainUI);
                fadeIn(ui);
                zoomIn(ui);
            } else {
                // Do a quick transition between the current overlay and the next.
                // Bug here: we don't pay attention to changes in outsideClickDismisses.
                explodeOut(currentOverlay.ui);
                fadeOutAndRemove(uiStack, currentOverlay.ui);
                uiStack.getChildren().add(ui);
                ui.setOpacity(0.0);
                fadeIn(ui, 100);
                zoomIn(ui, 100);
            }
            currentOverlay = this;
        }

        public void outsideClickDismisses () {
            stopClickPane.setOnMouseClicked((ev) -> done());
        }

        public void done () {
            checkGuiThread();
            if (ui == null) {
                return;  // In the middle of being dismissed and got an extra click.
            }
            explodeOut(ui);
            fadeOutAndRemove(uiStack, ui, stopClickPane);
            blurIn(mainUI);
            this.ui = null;
            this.controller = null;
            currentOverlay = null;
        }

		public T getController() {
			return controller;
		}
    }

    @Nullable
    private OverlayUI currentOverlay;

    public <T> OverlayUI<T> overlayUI (Node node, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(node, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            controller.getClass().getField("overlayUI").set(controller, pair);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
        pair.show();
        return pair;
    }

    /**
     * Loads the FXML file with the given name, blurs out the main UI and puts this one on top.
     */
    public <T> OverlayUI<T> overlayUI (String name) {
        try {
            checkGuiThread();
            // Load the UI from disk.
            URL location = GuiUtils.getResource(name);
            FXMLLoader loader = new FXMLLoader(location);
            Pane ui = loader.load();
            T controller = loader.getController();
            OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
            // Auto-magically set the overlayUI member, if it's there.
            try {
                if (controller != null) {
                    controller.getClass().getField("overlayUI").set(controller, pair);
                }
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                ignored.printStackTrace();
            }
            pair.show();
            return pair;
        } catch (IOException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }

    /**
     * Loads the FXML file with the given name, blurs out the main UI and puts this one on top.
     */
    public <T> OverlayUI<T> overlayUI (Pane ui, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            if (controller != null) {
                controller.getClass().getField("overlayUI").set(controller, pair);
            }
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
            ignored.printStackTrace();
        }
        pair.show();
        return pair;
    }

    @Override
    public void stop () throws Exception {

        try {
            if (walletAppKit != null) {
                walletAppKit.stopAsync().awaitTerminated(10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Forcibly terminate the JVM because Orchid likes to spew non-daemon threads everywhere.
        Runtime.getRuntime().exit(0);
    }

    public static void main (String[] args) {

        try {
            int id = Integer.parseInt(args[0]);
            CLIENTID = id;
        } catch (Exception e) {
            try {
                REQUEST = args[0];
            } catch (Exception f) {
            }

        }

        launch(args);
    }
}
