import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

class DatabaseDraft1 {
   private static final String FILE_ORDERS = "orders_log.txt";
   private static final String FILE_RESTOCK = "restock_log.txt";
   private static final String FILE_TIMELOG = "timelog.txt";
   private static final String FILE_AUDIT = "audit_trail.txt";
   private static final String FILE_INVOICES = "invoices.txt";
   private static Map<String, Product> inventory = new HashMap();
   private static Map<String, Integer> cart = new LinkedHashMap();
   private static List<String> detailedInvoices = new ArrayList();
   private static List<String> salesSummary = new ArrayList();
   private static List<String> restockHistory = new ArrayList();
   private static List<String> timeLogs = new ArrayList();
   private static double totalRevenue = (double)0.0F;
   private static int orderCount = 0;
   private static Scanner scanner;
   private static String ADMIN_PASSWORD;
   private static String EMPLOYEE_PASSWORD;
   private static User currentUser;

   DatabaseDraft1() {
   }

   private static void clearScreen() {
      try {
         String var0 = System.getProperty("os.name").toLowerCase();
         if (var0.contains("win")) {
            (new ProcessBuilder(new String[]{"cmd", "/c", "cls"})).inheritIO().start().waitFor();
         } else {
            (new ProcessBuilder(new String[]{"clear"})).inheritIO().start().waitFor();
         }
      } catch (Exception var2) {
         for(int var1 = 0; var1 < 50; ++var1) {
            System.out.println();
         }
      }
   }

   public static void main(String[] var0) {
      ensureFilesExist();
      loadOrderCount();
      seedData();
      loadStockFromDatabase();
      boolean var1 = true;

      while(var1) {
         clearScreen();
         System.out.println("╔══════════════════════════════════════╗");
         System.out.println("║    SIMPLE AUTOMATED POS SYSTEM       ║");
         System.out.println("╚══════════════════════════════════════╝");
         System.out.println("  1. Browse Categories & Shop");
         System.out.println("  2. View Cart & Checkout");
         System.out.println("  3. Staff Login");
         System.out.println("  4. Exit");
         System.out.print("\nSelect Option: ");
         switch (scanner.nextLine()) {
            case "1":
               categorySelectionPage();
               break;
            case "2":
               viewCartAndCheckout();
               break;
            case "3":
               clearScreen();
               currentUser = authenticate();
               if (currentUser != null) {
                  clearScreen();
                  PrintStream var10000 = System.out;
                  String var10001 = currentUser.getGreeting();
                  var10000.println("\n" + var10001 + ", " + currentUser.name + "!");
                  System.out.println("Login Time: " + currentUser.timeIn);
                  pause();
                  inventoryMenu();
                  currentUser = null;
               }
               break;
            case "4":
               var1 = false;
               DatabaseManager.closeConnection();
               break;
            default:
               System.out.println("Invalid option.");
               pause();
         }
      }

      clearScreen();
      System.out.println("Thank you! Goodbye.");
   }

   private static void pause() {
      System.out.print("\nPress Enter to continue...");
      scanner.nextLine();
   }

   private static void ensureFilesExist() {
      String[] var0 = new String[]{"orders_log.txt", "restock_log.txt", "timelog.txt", "audit_trail.txt", "invoices.txt"};

      for(String var4 : var0) {
         File var5 = new File(var4);
         if (!var5.exists()) {
            try {
               var5.createNewFile();
               Path var10000 = Paths.get(var4);
               String var10001 = getHeader(var4);
               Files.write(var10000, (var10001 + System.lineSeparator()).getBytes(), new OpenOption[]{StandardOpenOption.APPEND});
            } catch (IOException var7) {
               System.out.println("[WARNING] Could not create file: " + var4);
            }
         }
      }
   }

   private static String getHeader(String var0) {
      String var10000;
      switch (var0) {
         case "orders_log.txt" -> var10000 = "=== ORDERS LOG ===\r\nFormat: Order# | Date | Items | Subtotal | Discount | Total | Payment\r\n" + "-".repeat(72);
         case "restock_log.txt" -> var10000 = "=== RESTOCK LOG ===\r\nFormat: Date | ID | Product | Added | Old Stock | New Stock | By\r\n" + "-".repeat(72);
         case "timelog.txt" -> var10000 = "=== STAFF TIME LOG ===\r\nFormat: Date | Role | Action | Time\r\n" + "-".repeat(72);
         case "audit_trail.txt" -> var10000 = "=== AUDIT TRAIL ===\r\nAll system events recorded here.\r\n" + "-".repeat(72);
         case "invoices.txt" -> var10000 = "=== FULL INVOICES ===\r\n" + "-".repeat(72);
         default -> var10000 = "=== LOG FILE ===";
      }
      return var10000;
   }

   private static void appendToFile(String var0, String var1) {
      try {
         Files.write(Paths.get(var0), (var1 + System.lineSeparator()).getBytes(), new OpenOption[]{StandardOpenOption.APPEND});
      } catch (IOException var3) {
         System.out.println("[WARNING] Could not write to " + var0);
      }
   }

   private static void appendBlockToFile(String var0, String var1) {
      try {
         String var2 = var1 + System.lineSeparator() + "-".repeat(72) + System.lineSeparator();
         Files.write(Paths.get(var0), var2.getBytes(), new OpenOption[]{StandardOpenOption.APPEND});
      } catch (IOException var3) {
         System.out.println("[WARNING] Could not write to " + var0);
      }
   }

   private static void loadOrderCount() {
      try {
         List var0 = Files.readAllLines(Paths.get("orders_log.txt"));

         for(int var1 = var0.size() - 1; var1 >= 0; --var1) {
            String var2 = ((String)var0.get(var1)).trim();
            if (var2.startsWith("Order#")) {
               orderCount = Integer.parseInt(var2.split("\\|")[0].replace("Order#", "").trim());
               break;
            }
         }
      } catch (Exception var3) {
         orderCount = 0;
      }
   }

   private static void seedData() {
      inventory.put("101", new Product("Jasmine Rice 5k", "Grains", 20, 5, (double)250.0F, "2027-12-01"));
      inventory.put("102", new Product("Brown Rice 2k", "Grains", 15, 3, (double)180.0F, "2027-10-15"));
      inventory.put("103", new Product("White Bread", "Grains", 10, 2, (double)65.0F, "2026-03-31"));
      inventory.put("104", new Product("Spaghetti Pasta", "Grains", 25, 5, (double)45.0F, "2028-01-20"));
      inventory.put("105", new Product("Oatmeal 1kg", "Grains", 12, 4, (double)120.0F, "2027-05-12"));
      inventory.put("201", new Product("Fresh Milk 1L", "Dairy", 12, 4, (double)85.0F, "2026-04-05"));
      inventory.put("202", new Product("Cheddar Cheese", "Dairy", 30, 10, (double)55.0F, "2026-06-15"));
      inventory.put("203", new Product("Salted Butter", "Dairy", 15, 5, (double)95.0F, "2026-09-20"));
      inventory.put("204", new Product("Greek Yogurt", "Dairy", 8, 2, (double)110.0F, "2026-04-10"));
      inventory.put("205", new Product("Heavy Cream", "Dairy", 10, 3, (double)150.0F, "2026-05-01"));
      inventory.put("301", new Product("Canned Tuna", "Canned", 50, 10, (double)35.5F, "2028-11-30"));
      inventory.put("302", new Product("Corned Beef", "Canned", 40, 8, (double)48.0F, "2029-02-14"));
      inventory.put("303", new Product("Sardines", "Canned", 60, 15, (double)22.0F, "2028-08-22"));
      inventory.put("304", new Product("Green Peas", "Canned", 30, 5, (double)28.0F, "2028-05-10"));
      inventory.put("305", new Product("Condensed Milk", "Canned", 25, 5, (double)62.0F, "2027-12-25"));
      inventory.put("401", new Product("Potato Chips", "Snacks", 40, 10, (double)42.0F, "2026-08-15"));
      inventory.put("402", new Product("Chocolate Bar", "Snacks", 50, 12, (double)35.0F, "2027-01-10"));
      inventory.put("403", new Product("Mixed Nuts", "Snacks", 20, 5, (double)85.0F, "2026-11-05"));
      inventory.put("404", new Product("Biscuits Pack", "Snacks", 45, 10, (double)15.0F, "2026-12-20"));
      inventory.put("405", new Product("Gummy Bears", "Snacks", 30, 5, (double)25.0F, "2027-03-15"));
   }

   private static void loadStockFromDatabase() {
      try (java.sql.Connection conn = DatabaseManager.getConnection();
           java.sql.Statement stmt = conn.createStatement();
           java.sql.ResultSet rs = stmt.executeQuery(
               "SELECT id, stock, last_restocked FROM products")) {
         while (rs.next()) {
            String id = rs.getString("id");
            if (inventory.containsKey(id)) {
               inventory.get(id).stock = rs.getInt("stock");
               inventory.get(id).lastRestocked = rs.getString("last_restocked");
            }
         }
         System.out.println("[DB] Stock loaded from database.");
      } catch (Exception e) {
         System.out.println("[DB] Could not load stock: " + e.getMessage());
      }
   }

   private static void categorySelectionPage() {
      clearScreen();
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║          SELECT CATEGORY             ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.println("  1. Grains");
      System.out.println("  2. Dairy");
      System.out.println("  3. Canned Goods");
      System.out.println("  4. Snacks");
      System.out.println("  5. Back");
      System.out.print("\nChoose category: ");
      String var10000;
      switch (scanner.nextLine()) {
         case "1" -> var10000 = "Grains";
         case "2" -> var10000 = "Dairy";
         case "3" -> var10000 = "Canned";
         case "4" -> var10000 = "Snacks";
         default -> var10000 = null;
      }

      String var0 = var10000;
      if (var0 != null) {
         showProductsInCategory(var0);
      }
   }

   private static void showProductsInCategory(String var0) {
      clearScreen();
      System.out.println("╔══════════════════════════════════════╗");
      System.out.printf("║  %-36s║%n", "  " + var0.toUpperCase() + " SECTION");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.printf("%-5s | %-20s | %-8s | %s%n", "ID", "Name", "Price", "Stock");
      System.out.println("-".repeat(50));

      for(Map.Entry var2 : inventory.entrySet()) {
         if (((Product)var2.getValue()).category.equalsIgnoreCase(var0)) {
            Product var3 = (Product)var2.getValue();
            System.out.printf("%-5s | %-20s | %-8.2f | %d%n", var2.getKey(), var3.name, var3.price, var3.stock);
         }
      }

      System.out.println("-".repeat(50));
      System.out.print("\nEnter ID to add to cart (or '0' to go back): ");
      String var4 = scanner.nextLine();
      if (!var4.equals("0") && inventory.containsKey(var4)) {
         addToCart(var4);
      } else if (!var4.equals("0")) {
         System.out.println("Product ID not found.");
         pause();
      }
   }

   private static void addToCart(String var0) {
      clearScreen();
      Product var1 = (Product)inventory.get(var0);
      System.out.println("Adding: " + var1.name + " | Price: " + var1.price + " | Available Stock: " + var1.stock);
      System.out.print("Enter quantity: ");

      try {
         int var2 = Integer.parseInt(scanner.nextLine());
         int var3 = (Integer)cart.getOrDefault(var0, 0);
         if (var1.stock >= var3 + var2 && var2 > 0) {
            cart.put(var0, var3 + var2);
            System.out.println("\n✔ Added " + var2 + "x " + var1.name + " to cart!");
         } else {
            System.out.println("✘ Not enough stock available.");
         }
      } catch (Exception var4) {
         System.out.println("Invalid quantity.");
      }

      pause();
   }

   public static void viewCartAndCheckout() {
      if (cart.isEmpty()) {
         clearScreen();
         System.out.println("Your cart is empty.");
         pause();
      } else {
         clearScreen();
         displayCartContents();
         System.out.println("\n  1. Checkout Now");
         System.out.println("  2. Update/Remove Item");
         System.out.println("  3. Clear Cart");
         System.out.println("  4. Back");
         System.out.print("\nSelect: ");
         String var0 = scanner.nextLine();
         if (var0.equals("1")) {
            checkoutFlow();
         } else if (var0.equals("2")) {
            clearScreen();
            displayCartContents();
            System.out.print("\nEnter Product ID to modify: ");
            String var1 = scanner.nextLine();
            if (cart.containsKey(var1)) {
               clearScreen();
               System.out.println("  1. Remove all");
               System.out.println("  2. Reduce quantity");
               System.out.print("Select: ");
               String var2 = scanner.nextLine();
               if (var2.equals("1")) {
                  cart.remove(var1);
                  System.out.println("Item removed.");
               } else if (var2.equals("2")) {
                  System.out.print("Qty to reduce: ");
                  try {
                     int var3 = Integer.parseInt(scanner.nextLine());
                     if (var3 >= (Integer)cart.get(var1)) {
                        cart.remove(var1);
                     } else {
                        cart.put(var1, (Integer)cart.get(var1) - var3);
                     }
                     System.out.println("Cart updated.");
                  } catch (Exception var4) {
                     System.out.println("Invalid quantity.");
                  }
               }
            } else {
               System.out.println("ID not found in cart.");
            }
            pause();
         } else if (var0.equals("3")) {
            cart.clear();
            clearScreen();
            System.out.println("Cart cleared.");
            pause();
         }
      }
   }

   private static void checkoutFlow() {
      clearScreen();
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║            DISCOUNT CHECK            ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.print("Do you have a discount card? (PWD / Senior / None): ");
      String var0 = scanner.nextLine().toLowerCase();
      Object var1 = null;
      if (var0.equals("pwd") || var0.equals("senior")) {
         System.out.print("Enter ID Number: ");
         String var2 = scanner.nextLine();
         var1 = var0.equals("pwd") ? new PWDDiscount() : new SeniorDiscount();
         if (!((Discount)var1).isValid(var2)) {
            System.out.println("✘ Invalid ID format. Proceeding without discount.");
            var1 = null;
         } else {
            ((Discount)var1).idNumber = var2;
            System.out.println("✔ " + ((Discount)var1).getType() + " discount applied (20%).");
         }
         pause();
      }

      double var14 = calculateTotal();
      double var4 = var1 != null ? var14 * ((Discount)var1).getRate() : (double)0.0F;
      double var6 = var14 - var4;
      Object var8 = null;

      while(var8 == null) {
         clearScreen();
         System.out.println("╔══════════════════════════════════════╗");
         System.out.println("║           PAYMENT METHOD             ║");
         System.out.println("╚══════════════════════════════════════╝");
         System.out.printf("  Amount Due: PHP %.2f%n%n", var6);
         System.out.println("  1. Credit/Debit Card");
         System.out.println("  2. GCash");
         System.out.println("  3. Cash");
         System.out.print("\nSelect Method: ");
         String var9 = scanner.nextLine();
         if (var9.equals("1")) {
            var8 = new CardPayment();
            System.out.print("Enter Card Number (11 digits): ");
            String var10 = scanner.nextLine();
            if (!((Payment)var8).isValid(var10)) {
               System.out.println("✘ Invalid card number.");
               pause();
               var8 = null;
            } else {
               ((Payment)var8).accountInfo = var10;
               System.out.println("✔ Card accepted.");
            }
         } else if (var9.equals("2")) {
            var8 = new GCashPayment();
            System.out.print("Enter GCash Number (starts with 09, 11 digits): ");
            String var16 = scanner.nextLine();
            if (!((Payment)var8).isValid(var16)) {
               System.out.println("✘ Invalid GCash number.");
               pause();
               var8 = null;
            } else {
               ((Payment)var8).accountInfo = var16;
               System.out.println("✔ GCash number accepted.");
            }
         } else if (var9.equals("3")) {
            CashPayment var17 = new CashPayment();
            System.out.print("Enter Amount Tendered: PHP ");
            try {
               double var11 = Double.parseDouble(scanner.nextLine());
               if (var11 >= var6) {
                  var17.amountTendered = var11;
                  var17.change = var11 - var6;
                  var17.accountInfo = "N/A";
                  var8 = var17;
                  System.out.printf("✔ Change: PHP %.2f%n", var17.change);
               } else {
                  System.out.println("✘ Insufficient amount.");
                  pause();
               }
            } catch (Exception var13) {
               System.out.println("Invalid amount.");
               pause();
            }
         } else {
            System.out.println("Invalid option.");
            pause();
         }
      }

      ++orderCount;
      String var15 = getInvoiceString(orderCount, (Discount)var1, (Payment)var8, var14, var4, var6);
      clearScreen();
      System.out.print("Display invoice on screen? (y/n): ");
      if (scanner.nextLine().equalsIgnoreCase("y")) {
         clearScreen();
         System.out.println(var15);
      }

      finalizeStock();

      // ── DB: Save order and items ──
      int dbOrderId = DatabaseManager.saveOrder(var14, (Discount)var1, (Payment)var8, var4, var6);
      for (Map.Entry<String, Integer> entry : cart.entrySet()) {
         DatabaseManager.saveOrderItem(dbOrderId, entry.getKey(), inventory.get(entry.getKey()), entry.getValue());
      }
      DatabaseManager.saveAuditEvent("SALE", "Order#" + orderCount + " | Total: " + String.format("%.2f", var6));

      StringBuilder var18 = new StringBuilder();
      for(Map.Entry var12 : cart.entrySet()) {
         var18.append(((Product)inventory.get(var12.getKey())).name).append(" x").append(var12.getValue()).append("; ");
      }

      String var20 = var1 != null ? ((Discount)var1).getType() + " -" + String.format("%.2f", var4) : "None";
      String var21 = String.format("Order# %-4d | %s | Items: %s| Subtotal: %.2f | Discount: %s | Total: %.2f | Payment: %s", orderCount, new Date(), var18, var14, var20, var6, ((Payment)var8).getMethod());
      appendToFile("orders_log.txt", var21);
      appendBlockToFile("invoices.txt", var15);
      String var10001 = String.valueOf(new Date());
      appendToFile("audit_trail.txt", "[SALE]    " + var10001 + " | Order# " + orderCount + " | Total: " + String.format("%.2f", var6) + " | Payment: " + ((Payment)var8).getMethod());
      salesSummary.add(var21);
      detailedInvoices.add(var15);
      totalRevenue += var6;
      cart.clear();
      System.out.println("\n✔ Purchase complete! Invoice saved to invoices.txt");
      pause();
   }

   public static void displayCartContents() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║           YOUR SHOPPING CART         ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.printf("%-5s | %-20s | %-5s | %s%n", "ID", "Name", "Qty", "Subtotal");
      System.out.println("-".repeat(50));

      for(Map.Entry var1 : cart.entrySet()) {
         Product var2 = (Product)inventory.get(var1.getKey());
         System.out.printf("%-5s | %-20s | %-5d | PHP %.2f%n", var1.getKey(), var2.name, var1.getValue(), var2.price * (double)(Integer)var1.getValue());
      }

      System.out.println("-".repeat(50));
      System.out.printf("  TOTAL: PHP %.2f%n", calculateTotal());
   }

   private static double calculateTotal() {
      double var0 = (double)0.0F;
      for(Map.Entry var3 : cart.entrySet()) {
         var0 += ((Product)inventory.get(var3.getKey())).price * (double)(Integer)var3.getValue();
      }
      return var0;
   }

   private static String getInvoiceString(int var0, Discount var1, Payment var2, double var3, double var5, double var7) {
      StringBuilder var9 = new StringBuilder();
      var9.append("\n*** OFFICIAL INVOICE ***\n");
      var9.append(String.format("Order #: %d%n", var0));
      var9.append("Date   : ").append(new Date()).append("\n");
      var9.append("-".repeat(40)).append("\n");

      for(Map.Entry var11 : cart.entrySet()) {
         Product var12 = (Product)inventory.get(var11.getKey());
         var9.append(String.format("%-20s x%-4d  PHP %.2f%n", var12.name, var11.getValue(), var12.price * (double)(Integer)var11.getValue()));
      }

      var9.append("-".repeat(40)).append("\n");
      var9.append(String.format("SUBTOTAL      : PHP %.2f%n", var3));
      if (var1 != null) {
         var9.append(String.format("DISCOUNT (%s): -PHP %.2f (ID: %s)%n", var1.getType(), var5, var1.idNumber));
      }
      var9.append(String.format("TOTAL         : PHP %.2f%n", var7));
      var9.append(String.format("PAYMENT       : %s%n", var2.getMethod()));
      if (var2 instanceof CashPayment) {
         var9.append(String.format("CASH TENDERED : PHP %.2f%nCHANGE        : PHP %.2f%n", ((CashPayment)var2).amountTendered, ((CashPayment)var2).change));
      } else {
         var9.append(String.format("ACCOUNT NO    : %s%n", var2.accountInfo));
      }
      var9.append("*".repeat(40)).append("\n");
      return var9.toString();
   }

   private static void finalizeStock() {
      for(Map.Entry var1 : cart.entrySet()) {
         Product var2 = (Product)inventory.get(var1.getKey());
         var2.stock -= (Integer)var1.getValue();
         if (var2.stock <= var2.parLevel) {
            System.out.println("[ALERT] " + var2.name + " is LOW on stock! (" + var2.stock + " remaining)");
         }
         // ── DB: Update stock after sale ──
         DatabaseManager.updateStock((String)var1.getKey(), var2.stock, var2.lastRestocked);
      }
   }

   private static User authenticate() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║         STAFF AUTHENTICATION         ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.print("Enter Password: ");
      String var0 = scanner.nextLine();
      Object var1 = null;
      if (var0.equals(ADMIN_PASSWORD)) {
         var1 = new Manager();
      } else if (var0.equals(EMPLOYEE_PASSWORD)) {
         var1 = new Employee();
      }

      if (var1 != null) {
         String var2 = String.format("[LOGIN]  Date: %s | Role: %-8s | Name: %s | Time-In: %s", new Date(), ((User)var1).getRole(), ((User)var1).name, ((User)var1).timeIn);
         timeLogs.add(var2);
         appendToFile("timelog.txt", var2);
         appendToFile("audit_trail.txt", var2);
         DatabaseManager.saveTimeLog(((User)var1).getRole(), ((User)var1).name, "LOGIN");
         return (User)var1;
      } else {
         System.out.println("✘ Access Denied: Incorrect Password.");
         Date var10001 = new Date();
         appendToFile("audit_trail.txt", "[FAILED LOGIN] " + String.valueOf(var10001) + " | Reason: Wrong password");
         DatabaseManager.saveAuditEvent("FAILED_LOGIN", "Wrong password attempt at " + new Date());
         pause();
         return null;
      }
   }

   private static void inventoryMenu() {
      boolean var0 = false;

      while(!var0) {
         clearScreen();
         System.out.println("╔══════════════════════════════════════╗");
         PrintStream var10000 = System.out;
         Object[] var10002 = new Object[1];
         String var10005 = currentUser.getRole();
         var10002[0] = "  " + var10005 + " PANEL — " + currentUser.name;
         var10000.printf("║  %-36s║%n", var10002);
         System.out.println("╚══════════════════════════════════════╝");
         System.out.println("  1. Stock Report (Category View)");
         System.out.println("  2. Restock Item");
         if (currentUser instanceof Manager) {
            System.out.println("  3. View Restock Log");
            System.out.println("  4. View Sales History");
            System.out.println("  5. Change Manager Password");
            System.out.println("  6. View Time Logs / Attendance");
         }
         System.out.println("  7. Logout");
         System.out.print("\nSelect Option: ");
         String var1 = scanner.nextLine();
         if (var1.equals("1")) {
            clearScreen();
            displayCategoryStockMenu();
         } else if (var1.equals("2")) {
            clearScreen();
            restockItemFlow();
         } else if (var1.equals("3") && currentUser instanceof Manager) {
            clearScreen();
            displayRestockHistory();
            pause();
         } else if (var1.equals("4") && currentUser instanceof Manager) {
            clearScreen();
            displaySalesHistory();
         } else if (var1.equals("5") && currentUser instanceof Manager) {
            clearScreen();
            changePassword();
            pause();
         } else if (var1.equals("6") && currentUser instanceof Manager) {
            clearScreen();
            displayTimeLogs();
            pause();
         } else if (var1.equals("7")) {
            currentUser.timeOut = (new Date()).toString();
            String var2 = String.format("[LOGOUT] Date: %s | Role: %-8s | Name: %s | Time-Out: %s", new Date(), currentUser.getRole(), currentUser.name, currentUser.timeOut);
            timeLogs.add(var2);
            appendToFile("timelog.txt", var2);
            appendToFile("audit_trail.txt", var2);
            DatabaseManager.saveTimeLog(currentUser.getRole(), currentUser.name, "LOGOUT");
            clearScreen();
            System.out.println("Logged out successfully. Goodbye, " + currentUser.name + "!");
            pause();
            var0 = true;
         } else {
            System.out.println("Invalid option or insufficient permissions.");
            String var10001 = String.valueOf(new Date());
            appendToFile("audit_trail.txt", "[UNAUTHORIZED] " + var10001 + " | Role: " + currentUser.getRole() + " tried option: " + var1);
            pause();
         }
      }
   }

   private static void displayCategoryStockMenu() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║        STOCK REPORT BY CATEGORY      ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.println("  1. Grains");
      System.out.println("  2. Dairy");
      System.out.println("  3. Canned Goods");
      System.out.println("  4. Snacks");
      System.out.println("  5. View All");
      System.out.println("  6. Back");
      System.out.print("\nSelect Option: ");
      String var0 = scanner.nextLine();
      clearScreen();
      if (var0.equals("1")) {
         printStockTable("Grains");
      } else if (var0.equals("2")) {
         printStockTable("Dairy");
      } else if (var0.equals("3")) {
         printStockTable("Canned");
      } else if (var0.equals("4")) {
         printStockTable("Snacks");
      } else {
         if (!var0.equals("5")) return;
         printStockTable("Grains");
         printStockTable("Dairy");
         printStockTable("Canned");
         printStockTable("Snacks");
      }
      pause();
   }

   private static void printStockTable(String var0) {
      System.out.println("\n[ " + var0.toUpperCase() + " ]");
      System.out.printf("%-5s | %-20s | %-6s | %-12s | %s%n", "ID", "Name", "Stock", "Expiry", "Last Restocked");
      System.out.println("-".repeat(72));

      for(Map.Entry var2 : inventory.entrySet()) {
         if (((Product)var2.getValue()).category.equalsIgnoreCase(var0)) {
            Product var3 = (Product)var2.getValue();
            String var4 = var3.stock <= var3.parLevel ? var3.stock + " ⚠" : String.valueOf(var3.stock);
            System.out.printf("%-5s | %-20s | %-6s | %-12s | %s%n", var2.getKey(), var3.name, var4, var3.expiryDate, var3.lastRestocked);
         }
      }
   }

   private static void restockItemFlow() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║              RESTOCK ITEM            ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.print("Enter Product ID: ");
      String var0 = scanner.nextLine();
      if (inventory.containsKey(var0)) {
         Product var1 = (Product)inventory.get(var0);
         System.out.println("Product : " + var1.name + " | Current Stock: " + var1.stock);
         System.out.print("Qty to add: ");

         try {
            int var2 = Integer.parseInt(scanner.nextLine());
            int var3 = var1.stock;
            var1.stock += var2;
            var1.lastRestocked = (new Date()).toString();
            String var4 = String.format("Date: %s | ID: %-5s | Product: %-20s | Added: %-5d | Old: %-5d | New: %-5d | By: %s", new Date(), var0, var1.name, var2, var3, var1.stock, currentUser.getRole());
            restockHistory.add(var4);
            appendToFile("restock_log.txt", var4);
            appendToFile("audit_trail.txt", "[RESTOCK] " + var4);
            DatabaseManager.saveRestockLog(var0, var1.name, var2, var3, var1.stock, currentUser.getRole());
            DatabaseManager.updateStock(var0, var1.stock, var1.lastRestocked);
            System.out.println("\n✔ Stock updated! " + var1.name + ": " + var3 + " → " + var1.stock);
         } catch (Exception var5) {
            System.out.println("Invalid input.");
         }
      } else {
         System.out.println("✘ Product ID not found.");
      }
      pause();
   }

   private static void displayRestockHistory() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║           RESTOCK LOG (ALL)          ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.printf("%-5s | %-20s | %-6s | %-6s | %-6s | %-10s | %s%n", "ID", "Product", "Added", "Old", "New", "By", "Date");
      System.out.println("-".repeat(80));
      try (java.sql.Connection conn = DatabaseManager.getConnection();
           java.sql.Statement stmt = conn.createStatement();
           java.sql.ResultSet rs = stmt.executeQuery(
               "SELECT log_date, product_id, product_name, quantity_added, old_stock, new_stock, restocked_by FROM restock_log ORDER BY log_date DESC")) {
         boolean found = false;
         while (rs.next()) {
            found = true;
            System.out.printf("%-5s | %-20s | %-6d | %-6d | %-6d | %-10s | %s%n",
               rs.getString("product_id"),
               rs.getString("product_name"),
               rs.getInt("quantity_added"),
               rs.getInt("old_stock"),
               rs.getInt("new_stock"),
               rs.getString("restocked_by"),
               rs.getString("log_date"));
         }
         if (!found) System.out.println("No restock records found.");
      } catch (Exception e) {
         System.out.println("[DB] Could not load restock log: " + e.getMessage());
      }
   }

   private static void displaySalesHistory() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║         SALES HISTORY (ALL)          ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.printf("%-6s | %-22s | %-10s | %-10s | %-10s | %s%n",
         "Order", "Date", "Subtotal", "Discount", "Total", "Payment");
      System.out.println("-".repeat(80));

      double dbTotalRevenue = 0;
      try (java.sql.Connection conn = DatabaseManager.getConnection();
           java.sql.Statement stmt = conn.createStatement();
           java.sql.ResultSet rs = stmt.executeQuery(
               "SELECT order_id, order_date, subtotal, discount_amount, total, payment_method FROM orders ORDER BY order_date DESC")) {
         boolean found = false;
         while (rs.next()) {
            found = true;
            dbTotalRevenue += rs.getDouble("total");
            System.out.printf("%-6d | %-22s | %-10.2f | %-10.2f | %-10.2f | %s%n",
               rs.getInt("order_id"),
               rs.getString("order_date"),
               rs.getDouble("subtotal"),
               rs.getDouble("discount_amount"),
               rs.getDouble("total"),
               rs.getString("payment_method"));
         }
         if (!found) {
            System.out.println("No sales records found.");
            pause();
            return;
         }
      } catch (Exception e) {
         System.out.println("[DB] Could not load sales history: " + e.getMessage());
         pause();
         return;
      }

      System.out.printf("%nTOTAL REVENUE: PHP %.2f%n", dbTotalRevenue);
      System.out.print("\nView a specific order's items? (y/n): ");
      if (scanner.nextLine().equalsIgnoreCase("y")) {
         clearScreen();
         System.out.print("Enter Order Number: ");
         try {
            int orderId = Integer.parseInt(scanner.nextLine());
            System.out.println("\n--- Items for Order #" + orderId + " ---");
            System.out.printf("%-20s | %-5s | %-10s | %s%n", "Product", "Qty", "Unit Price", "Subtotal");
            System.out.println("-".repeat(55));
            try (java.sql.Connection conn = DatabaseManager.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT product_name, quantity, unit_price, subtotal FROM order_items WHERE order_id = ?")) {
               ps.setInt(1, orderId);
               java.sql.ResultSet rs = ps.executeQuery();
               boolean found = false;
               while (rs.next()) {
                  found = true;
                  System.out.printf("%-20s | %-5d | %-10.2f | %.2f%n",
                     rs.getString("product_name"),
                     rs.getInt("quantity"),
                     rs.getDouble("unit_price"),
                     rs.getDouble("subtotal"));
               }
               if (!found) System.out.println("Order not found.");
            }
         } catch (Exception e) {
            System.out.println("Invalid input.");
         }
      }
      pause();
   }

   private static void displayTimeLogs() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║      ATTENDANCE / TIME LOGS (ALL)    ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.printf("%-10s | %-15s | %-8s | %s%n", "Role", "Name", "Action", "Time");
      System.out.println("-".repeat(60));
      try (java.sql.Connection conn = DatabaseManager.getConnection();
           java.sql.Statement stmt = conn.createStatement();
           java.sql.ResultSet rs = stmt.executeQuery(
               "SELECT staff_role, staff_name, action, log_time FROM time_log ORDER BY log_time DESC")) {
         boolean found = false;
         while (rs.next()) {
            found = true;
            System.out.printf("%-10s | %-15s | %-8s | %s%n",
               rs.getString("staff_role"),
               rs.getString("staff_name"),
               rs.getString("action"),
               rs.getString("log_time"));
         }
         if (!found) System.out.println("No time log records found.");
      } catch (Exception e) {
         System.out.println("[DB] Could not load time logs: " + e.getMessage());
      }
   }

   private static void changePassword() {
      System.out.println("╔══════════════════════════════════════╗");
      System.out.println("║          CHANGE MANAGER PASSWORD     ║");
      System.out.println("╚══════════════════════════════════════╝");
      System.out.print("Enter current password: ");
      if (scanner.nextLine().equals(ADMIN_PASSWORD)) {
         System.out.print("Enter new password: ");
         ADMIN_PASSWORD = scanner.nextLine();
         System.out.println("✔ Password updated successfully!");
         String var10001 = String.valueOf(new Date());
         appendToFile("audit_trail.txt", "[PASSWORD CHANGE] " + var10001 + " | Changed by: " + currentUser.name);
         DatabaseManager.saveAuditEvent("PASSWORD_CHANGE", "Changed by: " + currentUser.name + " at " + new Date());
      } else {
         System.out.println("✘ Incorrect current password.");
         String var0 = String.valueOf(new Date());
         appendToFile("audit_trail.txt", "[FAILED PASSWORD CHANGE] " + var0 + " | Role: " + currentUser.getRole());
         DatabaseManager.saveAuditEvent("FAILED_PASSWORD_CHANGE", "Role: " + currentUser.getRole() + " at " + new Date());
      }
   }

   static {
      scanner = new Scanner(System.in);
      ADMIN_PASSWORD = "admin123";
      EMPLOYEE_PASSWORD = "employee123";
      currentUser = null;
   }
}