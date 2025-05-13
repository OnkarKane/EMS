
package emsystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.security.MessageDigest;
import java.sql.*;

public class EmployeeManagementDB extends JFrame {

    final String DB_URL = "jdbc:mysql://localhost:3306/employee_db";
    final String DB_USER = "root";
    final String DB_PASSWORD = "";

    String currentUser = null;
    String currentRole = null;

    // UI Components for main panel
    JTextField idField, nameField, deptField, salaryField;
    JTable table;
    DefaultTableModel tableModel;

    public EmployeeManagementDB() {
        showLoginScreen();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashed = md.digest(password.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashed) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void showLoginScreen() {
        JFrame loginFrame = new JFrame("Login / Register");
        loginFrame.setSize(400, 250);
        loginFrame.setLayout(new GridLayout(6, 2));
        loginFrame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        loginFrame.setLocationRelativeTo(null);

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JComboBox<String> roleBox = new JComboBox<>(new String[]{"user", "admin"});

        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");

        loginFrame.add(new JLabel("Username:"));
        loginFrame.add(usernameField);
        loginFrame.add(new JLabel("Password:"));
        loginFrame.add(passwordField);
        loginFrame.add(new JLabel("Role (Register only):"));
        loginFrame.add(roleBox);
        loginFrame.add(new JLabel());
        loginFrame.add(loginBtn);
        loginFrame.add(new JLabel());
        loginFrame.add(registerBtn);

        loginBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            try (Connection conn = getConnection()) {
                String hash = hashPassword(password);
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username=? AND password_hash=?");
                ps.setString(1, username);
                ps.setString(2, hash);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    currentUser = username;
                    currentRole = rs.getString("role");
                    loginFrame.dispose();
                    showMainApp();
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Invalid credentials.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        registerBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            String role = (String) roleBox.getSelectedItem();

            try (Connection conn = getConnection()) {
                String hash = hashPassword(password);
                PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)");
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.setString(3, role);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(loginFrame, "Registration successful.");
            } catch (SQLIntegrityConstraintViolationException ex) {
                JOptionPane.showMessageDialog(loginFrame, "Username already exists.");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        loginFrame.setVisible(true);
    }

    private void showMainApp() {
        setTitle("Employee Management - Logged in as: " + currentUser + " (" + currentRole + ")");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(5, 2));
        idField = new JTextField(); nameField = new JTextField();
        deptField = new JTextField(); salaryField = new JTextField();

        inputPanel.setBorder(BorderFactory.createTitledBorder("Employee Details"));
        inputPanel.add(new JLabel("ID:")); inputPanel.add(idField);
        inputPanel.add(new JLabel("Name:")); inputPanel.add(nameField);
        inputPanel.add(new JLabel("Department:")); inputPanel.add(deptField);
        inputPanel.add(new JLabel("Salary:")); inputPanel.add(salaryField);

        JPanel buttonPanel = new JPanel();
        JButton addBtn = new JButton("Add");
        JButton updateBtn = new JButton("Update");
        JButton deleteBtn = new JButton("Delete");
        JButton clearBtn = new JButton("Clear");

        buttonPanel.add(addBtn); buttonPanel.add(updateBtn);
        buttonPanel.add(deleteBtn); buttonPanel.add(clearBtn);
        inputPanel.add(buttonPanel);
        add(inputPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"ID", "Name", "Department", "Salary"}, 0);
        table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        loadEmployees();

        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = table.getSelectedRow();
                idField.setText(tableModel.getValueAt(row, 0).toString());
                nameField.setText(tableModel.getValueAt(row, 1).toString());
                deptField.setText(tableModel.getValueAt(row, 2).toString());
                salaryField.setText(tableModel.getValueAt(row, 3).toString());
            }
        });

        addBtn.addActionListener(e -> {
            if (!"admin".equals(currentRole)) {
                showMessage("Only admins can add employees.");
                return;
            }
            addEmployee();
        });

        updateBtn.addActionListener(e -> {
            if (!"admin".equals(currentRole)) {
                showMessage("Only admins can update employees.");
                return;
            }
            updateEmployee();
        });

        deleteBtn.addActionListener(e -> {
            if (!"admin".equals(currentRole)) {
                showMessage("Only admins can delete employees.");
                return;
            }
            deleteEmployee();
        });

        clearBtn.addActionListener(e -> clearFields());

        setVisible(true);
    }

    void loadEmployees() {
        try (Connection conn = getConnection()) {
            tableModel.setRowCount(0);
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM employees");
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("department"),
                    rs.getDouble("salary")
                });
            }
        } catch (SQLException ex) {
            showMessage("Error loading data: " + ex.getMessage());
        }
    }

    void addEmployee() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String dept = deptField.getText().trim();
        String salaryText = salaryField.getText().trim();

        if (id.isEmpty() || name.isEmpty() || dept.isEmpty() || salaryText.isEmpty()) {
            showMessage("All fields are required.");
            return;
        }

        try (Connection conn = getConnection()) {
            double salary = Double.parseDouble(salaryText);

            PreparedStatement check = conn.prepareStatement("SELECT * FROM employees WHERE id=?");
            check.setString(1, id);
            if (check.executeQuery().next()) {
                showMessage("Employee ID already exists.");
                return;
            }

            PreparedStatement ps = conn.prepareStatement("INSERT INTO employees VALUES (?, ?, ?, ?)");
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, dept);
            ps.setDouble(4, salary);
            ps.executeUpdate();

            showMessage("Employee added.");
            loadEmployees();
            clearFields();
        } catch (Exception e) {
            showMessage("Error: " + e.getMessage());
        }
    }

    void updateEmployee() {
        String id = idField.getText().trim();
        if (id.isEmpty()) {
            showMessage("Enter ID to update.");
            return;
        }

        try (Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement("UPDATE employees SET name=?, department=?, salary=? WHERE id=?");
            ps.setString(1, nameField.getText().trim());
            ps.setString(2, deptField.getText().trim());
            ps.setDouble(3, Double.parseDouble(salaryField.getText().trim()));
            ps.setString(4, id);

            if (ps.executeUpdate() > 0) {
                showMessage("Employee updated.");
                loadEmployees();
                clearFields();
            } else {
                showMessage("Employee not found.");
            }
        } catch (Exception e) {
            showMessage("Error: " + e.getMessage());
        }
    }

    void deleteEmployee() {
        String id = idField.getText().trim();
        if (id.isEmpty()) {
            showMessage("Enter ID to delete.");
            return;
        }

        try (Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM employees WHERE id=?");
            ps.setString(1, id);
            if (ps.executeUpdate() > 0) {
                showMessage("Employee deleted.");
                loadEmployees();
                clearFields();
            } else {
                showMessage("Employee not found.");
            }
        } catch (SQLException e) {
            showMessage("Error: " + e.getMessage());
        }
    }

    void clearFields() {
        idField.setText("");
        nameField.setText("");
        deptField.setText("");
        salaryField.setText("");
        table.clearSelection();
    }

    void showMessage(String msg) {
        JOptionPane.showMessageDialog(this, msg);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EmployeeManagementDB::new);
    }
}

