import javax.swing.*;
import java.sql.SQLException;

/**
 * APPLICATION ENTRY POINT
 * Initializes the MVC triad: DatabaseModel, MainView, and AppController.
 */
public class Main {
    public static void main(String[] args) {
        // Set native system look and feel for a modern, clean appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // 1. Model: Connects and initializes SQLite tables
                DatabaseModel model = new DatabaseModel();
                model.initDatabase();

                // 2. View: Constructs GUI screens
                MainView view = new MainView();

                // 3. Controller: Bridges Model and View with event handling
                new AppController(model, view);

                // Make GUI visible
                view.setVisible(true);

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null,
                        "Failed to initialize database: " + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });
    }
}
