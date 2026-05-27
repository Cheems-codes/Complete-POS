import java.time.LocalTime;
import java.util.*;

// --- USER CLASSES ---
abstract class User {
    String name;
    String timeIn;
    String timeOut;

    public User(String name) {
        this.name = name;
        this.timeIn = new Date().toString();
    }

    String getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return "Good Morning";
        if (hour >= 12 && hour < 18) return "Good Afternoon";
        return "Good Evening";
    }

    abstract String getRole();
}

class Employee extends User {
    public Employee() { super("Employee"); }
    @Override
    String getRole() { return "EMPLOYEE"; }
}

class Manager extends User {
    public Manager() { super("Manager"); }
    @Override
    String getRole() { return "MANAGER"; }
}

// --- DISCOUNT CLASSES ---
abstract class Discount {
    String idNumber;
    abstract boolean isValid(String id);
    abstract double getRate();
    abstract String getType();
}

class SeniorDiscount extends Discount {
    @Override
    boolean isValid(String id) {
        return id.startsWith("SNR-") && id.length() > 4;
    }
    @Override
    double getRate() { return 0.20; }
    @Override
    String getType() { return "Senior Citizen"; }
}

class PWDDiscount extends Discount {
    @Override
    boolean isValid(String id) {
        return id.startsWith("PWD-") && id.length() > 4;
    }
    @Override
    double getRate() { return 0.20; }
    @Override
    String getType() { return "PWD"; }
}

// --- PAYMENT CLASSES ---
abstract class Payment {
    String accountInfo;
    abstract boolean isValid(String info);
    abstract String getMethod();
}

class CardPayment extends Payment {
    @Override
    boolean isValid(String info) {
        return info.length() == 11 && info.matches("\\d+");
    }
    @Override
    String getMethod() { return "Credit/Debit Card"; }
}

class GCashPayment extends Payment {
    @Override
    boolean isValid(String info) {
        return info.startsWith("09") && info.length() == 11 && info.matches("\\d+");
    }
    @Override
    String getMethod() { return "GCash"; }
}

class CashPayment extends Payment {
    double amountTendered;
    double change;
    @Override
    boolean isValid(String info) { return true; }
    @Override
    String getMethod() { return "Cash"; }
}

class Product {
    String name;
    String category;
    int stock;
    int parLevel;
    double price;
    String lastRestocked;
    String expiryDate;

    public Product(String name, String category, int stock, int parLevel, double price, String expiryDate) {
        this.name = name;
        this.category = category;
        this.stock = stock;
        this.parLevel = parLevel;
        this.price = price;
        this.expiryDate = expiryDate;
        this.lastRestocked = "Initial Seed";
    }
}

public class Main {
    private static Map<String, Product> inventory = new HashMap<>();
    private static Map<String, Integer> cart = new HashMap<>();
    private static List<String> detailedInvoices = new ArrayList<>();
    private static List<String> salesSummary = new ArrayList<>();
    private static List<String> restockHistory = new ArrayList<>();
    private static List<String> timeLogs = new ArrayList<>();
    private static double totalRevenue = 0;
    private static Scanner scanner = new Scanner(System.in);
    private static String ADMIN_PASSWORD = "admin123";
    private static String EMPLOYEE_PASSWORD = "employee123";
    private static User currentUser = null;

    public static void main(String[] args) {
        seedData();

        boolean running = true;
        while (running) {
            System.out.println("\n===================================");
            System.out.println("     SIMPLE AUTOMATED POS SYSTEM     ");
            System.out.println("=====================================");
            System.out.println("1. Browse Categories & Shop");
            System.out.println("2. View Cart & Checkout");
            System.out.println("3. Staff Login");
            System.out.println("4. Exit");
            System.out.print("Select Option: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1": categorySelectionPage(); break;
                case "2": viewCartAndCheckout(); break;
                case "3":
                    currentUser = authenticate();
                    if (currentUser != null) {
                        System.out.println("\n" + currentUser.getGreeting() + ", " + currentUser.name + "!");
                        System.out.println("Login Time: " + currentUser.timeIn);
                        inventoryMenu();
                        currentUser = null; 
                    }
                    break;
                case "4": running = false; break;
                default: System.out.println("Invalid option.");
            }
        }
        System.out.println("Goodbye!");
    }

    private static void seedData() {
        inventory.put("101", new Product("Jasmine Rice 5k", "Grains", 20, 5, 250.00, "2027-12-01"));
        inventory.put("102", new Product("Brown Rice 2k", "Grains", 15, 3, 180.00, "2027-10-15"));
        inventory.put("103", new Product("White Bread", "Grains", 10, 2, 65.00, "2026-03-31"));
        inventory.put("104", new Product("Spaghetti Pasta", "Grains", 25, 5, 45.00, "2028-01-20"));
        inventory.put("105", new Product("Oatmeal 1kg", "Grains", 12, 4, 120.00, "2027-05-12"));
        inventory.put("201", new Product("Fresh Milk 1L", "Dairy", 12, 4, 85.00, "2026-04-05"));
        inventory.put("202", new Product("Cheddar Cheese", "Dairy", 30, 10, 55.00, "2026-06-15"));
        inventory.put("203", new Product("Salted Butter", "Dairy", 15, 5, 95.00, "2026-09-20"));
        inventory.put("204", new Product("Greek Yogurt", "Dairy", 8, 2, 110.00, "2026-04-10"));
        inventory.put("205", new Product("Heavy Cream", "Dairy", 10, 3, 150.00, "2026-05-01"));
        inventory.put("301", new Product("Canned Tuna", "Canned", 50, 10, 35.50, "2028-11-30"));
        inventory.put("302", new Product("Corned Beef", "Canned", 40, 8, 48.00, "2029-02-14"));
        inventory.put("303", new Product("Sardines", "Canned", 60, 15, 22.00, "2028-08-22"));
        inventory.put("304", new Product("Green Peas", "Canned", 30, 5, 28.00, "2028-05-10"));
        inventory.put("305", new Product("Condensed Milk", "Canned", 25, 5, 62.00, "2027-12-25"));
        inventory.put("401", new Product("Potato Chips", "Snacks", 40, 10, 42.00, "2026-08-15"));
        inventory.put("402", new Product("Chocolate Bar", "Snacks", 50, 12, 35.00, "2027-01-10"));
        inventory.put("403", new Product("Mixed Nuts", "Snacks", 20, 5, 85.00, "2026-11-05"));
        inventory.put("404", new Product("Biscuits Pack", "Snacks", 45, 10, 15.00, "2026-12-20"));
        inventory.put("405", new Product("Gummy Bears", "Snacks", 30, 5, 25.00, "2027-03-15"));
    }

    private static void categorySelectionPage() {
        System.out.println("\n--- SELECT CATEGORY ---");
        System.out.println("1. Grains\n2. Dairy\n3. Canned Goods\n4. Snacks\n5. Back");
        System.out.print("Choose category: ");
        String catChoice = scanner.nextLine();
        String cat = switch (catChoice) {
            case "1" -> "Grains";
            case "2" -> "Dairy";
            case "3" -> "Canned";
            case "4" -> "Snacks";
            default -> null;
        };
        if (cat != null) showProductsInCategory(cat);
    }

    private static void showProductsInCategory(String category) {
        System.out.println("\n--- " + category.toUpperCase() + " SECTION ---");
        for (Map.Entry<String, Product> entry : inventory.entrySet()) {
            if (entry.getValue().category.equalsIgnoreCase(category)) {
                Product p = entry.getValue();
                System.out.printf("ID: %-5s | %-20s | Price: %.2f | Stock: %d\n", entry.getKey(), p.name, p.price, p.stock);
            }
        }
        System.out.print("\nEnter ID to add to cart (or '0' to go back): ");
        String id = scanner.nextLine();
        if (!id.equals("0") && inventory.containsKey(id)) addToCart(id);
    }

    private static void addToCart(String id) {
        System.out.print("Enter quantity: ");
        try {
            int qty = Integer.parseInt(scanner.nextLine());
            Product p = inventory.get(id);
            int currentInCart = cart.getOrDefault(id, 0);
            if (p.stock >= (currentInCart + qty) && qty > 0) {
                cart.put(id, currentInCart + qty);
                System.out.println("Added to cart!");
            } else {
                System.out.println("Error: Not enough stock.");
            }
        } catch (Exception e) { System.out.println("Invalid quantity."); }
    }

    public static void viewCartAndCheckout() {
        if (cart.isEmpty()) { System.out.println("\nYour cart is empty."); return; }
        displayCartContents();
        System.out.println("\n1. Checkout Now\n2. Update/Remove Item\n3. Clear Cart\n4. Back");
        System.out.print("Select: ");
        String choice = scanner.nextLine();
        
        if (choice.equals("1")) {
            Discount activeDiscount = null;
            System.out.print("Do you have a discount card? (PWD/Senior/None): ");
            String discType = scanner.nextLine().toLowerCase();
            
            if (discType.equals("pwd") || discType.equals("senior")) {
                System.out.print("Enter ID Number: ");
                String idIn = scanner.nextLine();
                activeDiscount = discType.equals("pwd") ? new PWDDiscount() : new SeniorDiscount();
                
                if (!activeDiscount.isValid(idIn)) {
                    System.out.println("Invalid ID format! Proceeding without discount.");
                    activeDiscount = null;
                } else {
                    activeDiscount.idNumber = idIn;
                    System.out.println("Success: " + activeDiscount.getType() + " ID [" + idIn + "] acknowledged. 20% discount applied.");
                }
            }

            double subTotal = calculateTotal();
            double discAmount = (activeDiscount != null) ? subTotal * activeDiscount.getRate() : 0;
            double finalTotal = subTotal - discAmount;

            Payment activePayment = null;
            while (activePayment == null) {
                System.out.println("\n--- PAYMENT METHOD ---");
                System.out.println("1. Credit/Debit Card\n2. GCash\n3. Cash");
                System.out.print("Select Method: ");
                String payChoice = scanner.nextLine();

                if (payChoice.equals("1")) {
                    activePayment = new CardPayment();
                    System.out.print("Enter Card Number (11 digits): ");
                    String accNum = scanner.nextLine();
                    if (!activePayment.isValid(accNum)) { System.out.println("Invalid number."); activePayment = null; } 
                    else { activePayment.accountInfo = accNum; }
                } else if (payChoice.equals("2")) {
                    activePayment = new GCashPayment();
                    System.out.print("Enter GCash Number (11 digits): ");
                    String accNum = scanner.nextLine();
                    if (!activePayment.isValid(accNum)) { System.out.println("Invalid number."); activePayment = null; } 
                    else { activePayment.accountInfo = accNum; }
                } else if (payChoice.equals("3")) {
                    CashPayment cp = new CashPayment();
                    System.out.printf("Total to pay: %.2f\n", finalTotal);
                    System.out.print("Enter Amount Tendered: ");
                    try {
                        double tendered = Double.parseDouble(scanner.nextLine());
                        if (tendered >= finalTotal) {
                            cp.amountTendered = tendered;
                            cp.change = tendered - finalTotal;
                            cp.accountInfo = "N/A";
                            activePayment = cp;
                            System.out.printf("Change: %.2f\n", cp.change);
                        } else { System.out.println("Insufficient cash."); }
                    } catch (Exception e) { System.out.println("Invalid amount."); }
                }
            }
            
            String invoice = getInvoiceString(activeDiscount, activePayment, subTotal, discAmount, finalTotal);
            System.out.print("Display/Print Invoice? (y/n): ");
            if (scanner.nextLine().equalsIgnoreCase("y")) System.out.println(invoice);
            
            finalizeStock();
            salesSummary.add("ID: " + (salesSummary.size() + 1) + " | Date: " + new Date() + " | Total: " + finalTotal);
            detailedInvoices.add(invoice);
            totalRevenue += finalTotal;
            cart.clear();
            System.out.println("Purchase recorded.");
        } else if (choice.equals("2")) {
            System.out.print("Enter Product ID to modify: ");
            String id = scanner.nextLine();
            if (cart.containsKey(id)) {
                System.out.println("1. Remove all\n2. Reduce quantity");
                String sub = scanner.nextLine();
                if (sub.equals("1")) cart.remove(id);
                else if (sub.equals("2")) {
                    System.out.print("Qty to reduce: ");
                    try {
                        int r = Integer.parseInt(scanner.nextLine());
                        if (r >= cart.get(id)) cart.remove(id);
                        else cart.put(id, cart.get(id) - r);
                        System.out.println("Cart updated.");
                    } catch (Exception e) { System.out.println("Invalid quantity."); }
                }
            }
        } else if (choice.equals("3")) cart.clear();
    }

    public static void displayCartContents() {
        System.out.println("\n--- YOUR SHOPPING CART ---");
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            Product p = inventory.get(entry.getKey());
            System.out.printf("%-5s | %-20s x %d | Subtotal: %.2f\n", entry.getKey(), p.name, entry.getValue(), (p.price * entry.getValue()));
        }
        System.out.printf("CURRENT TOTAL: %.2f\n", calculateTotal());
    }

    private static double calculateTotal() {
        double total = 0;
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            total += inventory.get(entry.getKey()).price * entry.getValue();
        }
        return total;
    }

    private static String getInvoiceString(Discount disc, Payment pay, double sub, double dAmt, double total) {
        StringBuilder sb = new StringBuilder("\n*** OFFICIAL INVOICE ***\n");
        sb.append("Date: ").append(new Date()).append("\n");
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            Product p = inventory.get(entry.getKey());
            sb.append(String.format("%-20s %-5d %.2f\n", p.name, entry.getValue(), (p.price * entry.getValue())));
        }
        sb.append("---------------------------\n");
        sb.append(String.format("SUBTOTAL: %.2f\n", sub));
        if (disc != null) sb.append(String.format("DISCOUNT (%s): -%.2f (ID: %s)\n", disc.getType(), dAmt, disc.idNumber));
        sb.append(String.format("PAYMENT METHOD: %s\n", pay.getMethod()));
        if (pay instanceof CashPayment) {
            sb.append(String.format("CASH TENDERED: %.2f\nCHANGE: %.2f\n", ((CashPayment)pay).amountTendered, ((CashPayment)pay).change));
        } else { sb.append(String.format("ACCOUNT NO: %s\n", pay.accountInfo)); }
        sb.append(String.format("TOTAL: %.2f\n", total));
        sb.append("************************\n");
        return sb.toString();
    }

    private static void finalizeStock() {
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            Product p = inventory.get(entry.getKey());
            p.stock -= entry.getValue();
            if (p.stock <= p.parLevel) System.out.println("[ALERT]: " + p.name + " low stock!");
        }
    }

    private static User authenticate() {
        System.out.println("\n--- STAFF AUTHENTICATION ---");
        System.out.print("Enter Password: ");
        String pass = scanner.nextLine();
        User u = null;
        if (pass.equals(ADMIN_PASSWORD)) u = new Manager();
        else if (pass.equals(EMPLOYEE_PASSWORD)) u = new Employee();
        
        if (u != null) {
            timeLogs.add(u.getRole() + " (" + u.name + ") logged in at: " + u.timeIn);
            return u;
        } else {
            System.out.println("Access Denied: Incorrect Password.");
            return null;
        }
    }

    private static void inventoryMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- " + currentUser.getRole() + " PANEL ---");
            System.out.println("1. Stock Report (Category View)");
            System.out.println("2. Restock Item");
            if (currentUser instanceof Manager) {
                System.out.println("3. View Restock Log");
                System.out.println("4. View Sales History");
                System.out.println("5. Change Manager Password");
                System.out.println("6. View Time Logs");
            }
            System.out.println("7. Logout");
            System.out.print("Select Option: ");
            
            String choice = scanner.nextLine();
            if (choice.equals("1")) displayCategoryStockMenu();
            else if (choice.equals("2")) restockItemFlow();
            else if (choice.equals("3") && currentUser instanceof Manager) displayRestockHistory();
            else if (choice.equals("4") && currentUser instanceof Manager) displaySalesHistory();
            else if (choice.equals("5") && currentUser instanceof Manager) changePassword();
            else if (choice.equals("6") && currentUser instanceof Manager) displayTimeLogs();
            else if (choice.equals("7")) {
                currentUser.timeOut = new Date().toString();
                timeLogs.add(currentUser.getRole() + " (" + currentUser.name + ") logged out at: " + currentUser.timeOut);
                back = true;
            } else {
                System.out.println("Invalid option or No Permission.");
            }
        }
    }

    private static void displayCategoryStockMenu() {
        System.out.println("\n--- VIEW STOCK BY CATEGORY ---");
        System.out.println("1. Grains\n2. Dairy\n3. Canned Goods\n4. Snacks\n5. View All\n6. Back");
        System.out.print("Select Option: ");
        String choice = scanner.nextLine();
        if (choice.equals("1")) printStockTable("Grains");
        else if (choice.equals("2")) printStockTable("Dairy");
        else if (choice.equals("3")) printStockTable("Canned");
        else if (choice.equals("4")) printStockTable("Snacks");
        else if (choice.equals("5")) {
            printStockTable("Grains"); printStockTable("Dairy");
            printStockTable("Canned"); printStockTable("Snacks");
        }
    }

    private static void printStockTable(String cat) {
        System.out.println("\n[" + cat.toUpperCase() + "]");
        System.out.printf("%-5s | %-20s | %-8s | %-12s | %-20s\n", "ID", "Name", "Stock", "Expiry", "Last Restocked");
        System.out.println("-------------------------------------------------------------------------------");
        for (Map.Entry<String, Product> e : inventory.entrySet()) {
            if (e.getValue().category.equalsIgnoreCase(cat)) {
                Product p = e.getValue();
                System.out.printf("%-5s | %-20s | %-8d | %-12s | %-20s\n", e.getKey(), p.name, p.stock, p.expiryDate, p.lastRestocked);
            }
        }
    }

    private static void restockItemFlow() {
        System.out.print("Enter ID: "); String id = scanner.nextLine();
        if(inventory.containsKey(id)) {
            System.out.print("Qty to add: ");
            try {
                int addQty = Integer.parseInt(scanner.nextLine());
                Product p = inventory.get(id);
                p.stock += addQty;
                p.lastRestocked = new Date().toString();
                restockHistory.add("ID: " + id + " | Added: " + addQty + " | New Total: " + p.stock + " | Date: " + p.lastRestocked + " | By: " + currentUser.getRole());
                System.out.println("Stock updated.");
            } catch (Exception e) { System.out.println("Invalid input."); }
        } else { System.out.println("ID not found."); }
    }

    private static void displayRestockHistory() {
        System.out.println("\n--- RESTOCK LOG ---");
        if (restockHistory.isEmpty()) System.out.println("No records.");
        else for (String entry : restockHistory) System.out.println(entry);
    }

    private static void displaySalesHistory() {
        System.out.println("\n--- SALES HISTORY ---");
        if (salesSummary.isEmpty()) { System.out.println("No transactions."); return; }
        for (String log : salesSummary) System.out.println(log);
        System.out.printf("TOTAL REVENUE: %.2f\n", totalRevenue);
        System.out.print("\nView specific invoice? (y/n): ");
        if (scanner.nextLine().equalsIgnoreCase("y")) {
            System.out.print("Enter Transaction ID: ");
            try {
                int id = Integer.parseInt(scanner.nextLine());
                if (id > 0 && id <= detailedInvoices.size()) System.out.println(detailedInvoices.get(id - 1));
            } catch (Exception e) { System.out.println("Invalid ID."); }
        }
    }

    private static void displayTimeLogs() {
        System.out.println("\n--- ATTENDANCE & TIME LOGS ---");
        if (timeLogs.isEmpty()) System.out.println("No logs recorded.");
        else for (String log : timeLogs) System.out.println(log);
    }

    private static void changePassword() {
        System.out.print("Enter current manager password: ");
        if (scanner.nextLine().equals(ADMIN_PASSWORD)) {
            System.out.print("Enter new manager password: ");
            ADMIN_PASSWORD = scanner.nextLine();
            System.out.println("Password updated!");
        } else { System.out.println("Incorrect current password."); }
    }
}