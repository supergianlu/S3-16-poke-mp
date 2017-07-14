package view;

import controller.GameControllerImpl;

import java.util.List;

public class ClassicDialoguePanel extends DialoguePanel {
    private GameControllerImpl gameController;

    public ClassicDialoguePanel(final GameControllerImpl gameController, final List<String> dialogues) {
        super(dialogues);
        this.gameController = gameController;
        this.gameController.gamePanel().setFocusable(false);
        if(dialogues.size() == 1) setFinalButtons();
    }

    @Override
    protected void setFinalButtons() {
        buttons.get(0).setText("close");
        buttons.get(0).addActionListener(e -> {
            response = buttons.get(0).getText();
            gameController.gamePanel().setFocusable(true);
            this.setVisible(false);
        });
    }
}
