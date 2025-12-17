package com.example;

import com.example.controller.AiController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AiAssistantFX extends Application {
    
    private AiController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(AiAssistantFX.class.getResource("ai-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 700);
        
        // 컨트롤러 인스턴스 가져오기 (종료 시 리소스 해제를 위해)
        controller = fxmlLoader.getController();

        stage.setTitle("테스트");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // 앱 종료 시 파이썬 프로세스도 같이 종료
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}