package com.techsenger.ceffx.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.io.File;

// IMPORTS DO SEU MOTOR GRAFICO CUSTOMIZADO
import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefClient;
import com.techsenger.ceffx.core.CefSettings;
import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.browser.CefRendererD3D;

public class Demo extends Application {

    private static CefApp cefAppInstance;
    private CefClient client;
    private CefBrowser browser;

    public static void main(String[] args) {
        try {
            // 1. Inicialização idêntica do Chromium antes do JavaFX
            CefSettings settings = new CefSettings();
            settings.windowless_rendering_enabled = true; // Ativa nosso ImageView OSR
            settings.multi_threaded_message_loop = true;
            settings.external_message_pump = false;
            settings.command_line_args_disabled = false;

            CefApp.startup(args);
            cefAppInstance = CefApp.getInstance(settings);

            // 2. Dispara o JavaFX
            Application.launch(Demo.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // Executa a montagem do browser de forma segura usando o runLater do CEF
            CefApp.runLater(() -> {
                try {
                    client = cefAppInstance.createClient();

                    // LIGA O MOTOR ACELERADO DIRECT3D 11 DE 2026 QUE COMPILAMOS!
                    client.setRendererFactory(() -> new CefRendererD3D());

                    // Cria o browser carregando o YouTube ou Google para o print
                    browser = client.createBrowser("https://youtube.com", true, false);

                    Platform.runLater(() -> {
                        // Componentes de uma barra de navegação moderna e limpa
                        TextField urlBar = new TextField("https://youtube.com");
                        Button btnIr = new Button("Navegar");
                        btnIr.setStyle("-fx-background-color: #0078d4; -fx-text-fill: white; -fx-font-weight: bold;");

                        btnIr.setOnAction(e -> browser.loadURL(urlBar.getText()));
                        urlBar.setOnAction(e -> browser.loadURL(urlBar.getText()));

                        HBox topBar = new HBox(10, urlBar, btnIr);
                        HBox.setHgrow(urlBar, Priority.ALWAYS);
                        topBar.setStyle("-fx-padding: 10; -fx-background-color: #202020;");

                        // Contêiner central que recebe o ImageView blindado
                        StackPane centerPane = new StackPane(browser.getPane());

                        BorderPane mainLayout = new BorderPane();
                        mainLayout.setTop(topBar);
                        mainLayout.setCenter(centerPane);

                        Scene scene = new Scene(mainLayout, 1280, 720);

                        // Sincroniza o resize geométrico para testar a nossa blindagem de 4K
                        primaryStage.widthProperty().addListener((obs, old, val) -> browser.getPane().setPrefWidth(val.doubleValue()));
                        primaryStage.heightProperty().addListener((obs, old, val) -> browser.getPane().setPrefHeight(val.doubleValue()));

                        primaryStage.setTitle("CEFFX Fork - Engine de Ultra Performance GPU (Direct3D 11)");
                        primaryStage.setScene(scene);
                        primaryStage.setOnCloseRequest(e -> {
                            // Evita o fechamento padrão do JavaFX para dar tempo ao Chromium de limpar a memória
                            e.consume();

                            // Dispara o encerramento limpo na thread correta
                            fecharAplicacaoSeguro(primaryStage);
});
                        primaryStage.show();
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   private void fecharAplicacaoSeguro(Stage stage) {
    // 1. Esconde a janela imediatamente para dar feedback visual ao usuário
    if (stage != null) {
        stage.hide();
    }

    // 2. Executa o descarte do CefApp de forma assíncrona dentro da Thread do CEF
    CefApp.runLater(() -> {
        try {
            if (cefAppInstance != null) {
                cefAppInstance.dispose();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // 3. O golpe de misericórdia: Após desligar as DLLs, mata o processo no Windows
            Platform.runLater(() -> {
                Platform.exit();
                System.exit(0); // Garante que o Maven e o processo sumam do gerenciador
            });
        }
    });
}

    @Override
    public void stop() throws Exception {
        // Fallback caso o fechamento não passe pelo setOnCloseRequest
        if (cefAppInstance != null) {
            fecharAplicacaoSeguro(null);
        }
        super.stop();
    }

}
