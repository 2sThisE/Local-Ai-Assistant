package com.example;

import com.example.controller.AiController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;

public class AiAssistantFX extends Application {
    
    private AiController controller;

    @Override
    public void start(Stage stage) throws IOException {
        Font font1=Font.loadFont(getClass().getResourceAsStream("/com/example/fonts/Pretendard-Regular.ttf"), 12);
        Font font2=Font.loadFont(getClass().getResourceAsStream("/com/example/fonts/Pretendard-Medium.ttf"), 12);
        Font font3=Font.loadFont(getClass().getResourceAsStream("/com/example/fonts/Pretendard-Bold.ttf"), 12);
        if (font1 != null) {
            System.out.println("폰트 로딩 성공: " + font1.getFamily()); // "Pretendard"라고 떠야 함
        } else {
            System.out.println("폰트 로딩 실패: 경로를 확인하세요.");
        }
        if(font2 != null){
            System.out.println("폰트 로딩 성공: " + font2.getFamily());
        }else{
            System.out.println("폰트 로딩 실패: 경로를 확인하세요.");
        }

        FXMLLoader fxmlLoader = new FXMLLoader(AiAssistantFX.class.getResource("ai-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 720);
        
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