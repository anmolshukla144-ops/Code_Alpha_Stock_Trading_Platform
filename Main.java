import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {

    static Scanner sc = new Scanner(System.in);
    static final String FILE = "stock_platform_data.txt";
    static Market market = new Market();
    static User user = new User("Demo User", 100000);

    public static void main(String[] args) {
        market.loadStocks();
        loadData();

        System.out.println("==================================");
        System.out.println("      STOCK TRADING PLATFORM");
        System.out.println("==================================");
        System.out.println("Welcome, " + user.name);
        System.out.println("Demo simulation only; not live market data.");

        boolean running = true;

        while (running) {
            System.out.println("\n1. View Market Data");
            System.out.println("2. Buy Stocks");
            System.out.println("3. Sell Stocks");
            System.out.println("4. View Portfolio");
            System.out.println("5. Portfolio Performance");
            System.out.println("6. Account Summary");
            System.out.println("7. Transaction History");
            System.out.println("8. Save Data");
            System.out.println("0. Exit");

            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1:
                    market.simulatePrices();
                    market.displayStocks();
                    break;
                case 2:
                    buyStock();
                    break;
                case 3:
                    sellStock();
                    break;
                case 4:
                    user.portfolio.displayHoldings(market);
                    break;
                case 5:
                    user.portfolio.displayPerformance(market);
                    break;
                case 6:
                    user.displaySummary(market);
                    break;
                case 7:
                    user.portfolio.displayTransactions();
                    break;
                case 8:
                    saveData();
                    System.out.println("Data saved successfully.");
                    break;
                case 0:
                    saveData();
                    System.out.println("Data saved. Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }

        sc.close();
    }

    static int readInt(String message) {
        while (true) {
            System.out.print(message);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    static String readText(String message) {
        System.out.print(message);
        return sc.nextLine().trim().toUpperCase(Locale.ROOT);
    }

    static void buyStock() {
        market.displayStocks();

        String symbol = readText("Enter stock symbol: ");
        Stock stock = market.getStock(symbol);

        if (stock == null) {
            System.out.println("Stock not found.");
            return;
        }

        int quantity = readInt("Enter quantity: ");

        if (quantity <= 0) {
            System.out.println("Quantity must be greater than zero.");
            return;
        }

        double cost = stock.price * quantity;

        if (cost > user.cash) {
            System.out.println("Insufficient account balance.");
            return;
        }

        user.buy(stock, quantity);

        System.out.printf(Locale.US,
                "Bought %d shares of %s. Total cost: Rs. %.2f%n",
                quantity, symbol, cost);

        saveData();
    }

    static void sellStock() {
        user.portfolio.displayHoldings(market);

        String symbol = readText("Enter stock symbol: ");
        Stock stock = market.getStock(symbol);

        if (stock == null) {
            System.out.println("Stock not found.");
            return;
        }

        int quantity = readInt("Enter quantity to sell: ");

        if (quantity <= 0) {
            System.out.println("Quantity must be greater than zero.");
            return;
        }

        if (!user.sell(stock, quantity)) {
            System.out.println("You do not own enough shares.");
            return;
        }

        System.out.printf(Locale.US,
                "Sold %d shares of %s for Rs. %.2f%n",
                quantity, symbol, stock.price * quantity);

        saveData();
    }

    static void saveData() {
        try (PrintWriter out = new PrintWriter(new FileWriter(FILE))) {
            out.println("CASH|" + user.cash);

            for (Stock s : market.stocks.values()) {
                out.println("PRICE|" + s.symbol + "|" + s.price);
            }

            for (Map.Entry<String, Holding> e :
                    user.portfolio.holdings.entrySet()) {
                Holding h = e.getValue();
                out.println("HOLDING|" + e.getKey() + "|"
                        + h.quantity + "|" + h.averageCost);
            }

            for (Transaction t : user.portfolio.transactions) {
                out.println("TX|" + t.toFileLine());
            }

        } catch (IOException e) {
            System.out.println("Error saving data: " + e.getMessage());
        }
    }

    static void loadData() {
        File file = new File(FILE);

        if (!file.exists()) {
            return;
        }

        double cash = 100000;
        Map<String, Holding> holdings = new LinkedHashMap<>();
        List<Transaction> transactions = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = in.readLine()) != null) {
                String[] p = line.split("\\|", -1);

                switch (p[0]) {
                    case "CASH":
                        cash = Double.parseDouble(p[1]);
                        break;

                    case "PRICE":
                        Stock s = market.getStock(p[1]);
                        if (s != null) {
                            s.price = Double.parseDouble(p[2]);
                        }
                        break;

                    case "HOLDING":
                        holdings.put(p[1],
                                new Holding(
                                        Integer.parseInt(p[2]),
                                        Double.parseDouble(p[3])));
                        break;

                    case "TX":
                        if (p.length == 7) {
                            transactions.add(Transaction.fromParts(p, 1));
                        }
                        break;

                    default:
                        break;
                }
            }

            user = new User("Demo User", cash);
            user.portfolio.holdings.putAll(holdings);
            user.portfolio.transactions.addAll(transactions);

            for (Transaction t : transactions) {
                if (t.type.equals("SELL")) {
                    user.portfolio.realizedPL += t.profitLoss;
                }
            }

        } catch (IOException | NumberFormatException e) {
            System.out.println("Saved data could not be loaded.");
        }
    }
}

class Stock {
    String symbol;
    String company;
    double price;
    double initialPrice;

    Stock(String symbol, String company, double price) {
        this.symbol = symbol;
        this.company = company;
        this.price = price;
        this.initialPrice = price;
    }

    void simulatePrice() {
        double change = Math.random() * 4 - 2;
        price = Math.max(1, price * (1 + change / 100));
    }
}

class Market {
    Map<String, Stock> stocks = new LinkedHashMap<>();

    void loadStocks() {
        stocks.put("TCS",
                new Stock("TCS", "Tata Consultancy Services", 4200));
        stocks.put("INFY",
                new Stock("INFY", "Infosys", 1850));
        stocks.put("RELIANCE",
                new Stock("RELIANCE", "Reliance Industries", 2900));
        stocks.put("HDFCBANK",
                new Stock("HDFCBANK", "HDFC Bank", 1700));
        stocks.put("WIPRO",
                new Stock("WIPRO", "Wipro", 520));
    }

    Stock getStock(String symbol) {
        return stocks.get(symbol);
    }

    void simulatePrices() {
        for (Stock s : stocks.values()) {
            s.simulatePrice();
        }
    }

    void displayStocks() {
        System.out.println("\n------------ MARKET DATA ------------");
        System.out.printf("%-12s %-32s %12s %12s%n",
                "SYMBOL", "COMPANY", "PRICE", "CHANGE");

        for (Stock s : stocks.values()) {
            double change =
                    (s.price - s.initialPrice) / s.initialPrice * 100;

            System.out.printf(Locale.US,
                    "%-12s %-32s %12.2f %+11.2f%%%n",
                    s.symbol, s.company, s.price, change);
        }

        System.out.println("Prices are simulated, not live.");
    }
}

class Holding {
    int quantity;
    double averageCost;

    Holding(int quantity, double averageCost) {
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    void add(int qty, double price) {
        double total = quantity * averageCost + qty * price;
        quantity += qty;
        averageCost = total / quantity;
    }

    void remove(int qty) {
        quantity -= qty;

        if (quantity == 0) {
            averageCost = 0;
        }
    }
}

class Portfolio {
    Map<String, Holding> holdings = new LinkedHashMap<>();
    List<Transaction> transactions = new ArrayList<>();
    double realizedPL = 0;

    void buy(Stock stock, int qty) {
        Holding h = holdings.get(stock.symbol);

        if (h == null) {
            holdings.put(stock.symbol,
                    new Holding(qty, stock.price));
        } else {
            h.add(qty, stock.price);
        }

        transactions.add(new Transaction(
                "BUY", stock.symbol, qty, stock.price, 0));
    }

    boolean sell(Stock stock, int qty) {
        Holding h = holdings.get(stock.symbol);

        if (h == null || h.quantity < qty || qty <= 0) {
            return false;
        }

        double pl = (stock.price - h.averageCost) * qty;
        realizedPL += pl;

        h.remove(qty);

        if (h.quantity == 0) {
            holdings.remove(stock.symbol);
        }

        transactions.add(new Transaction(
                "SELL", stock.symbol, qty, stock.price, pl));

        return true;
    }

    double marketValue(Market market) {
        double total = 0;

        for (Map.Entry<String, Holding> e : holdings.entrySet()) {
            Stock s = market.getStock(e.getKey());

            if (s != null) {
                total += s.price * e.getValue().quantity;
            }
        }

        return total;
    }

    double unrealizedPL(Market market) {
        double total = 0;

        for (Map.Entry<String, Holding> e : holdings.entrySet()) {
            Stock s = market.getStock(e.getKey());
            Holding h = e.getValue();

            if (s != null) {
                total += (s.price - h.averageCost) * h.quantity;
            }
        }

        return total;
    }

    void displayHoldings(Market market) {
        System.out.println("\n------------ YOUR PORTFOLIO ------------");

        if (holdings.isEmpty()) {
            System.out.println("No stocks owned.");
            return;
        }

        System.out.printf("%-12s %10s %14s %14s %14s%n",
                "SYMBOL", "QUANTITY", "AVG COST",
                "CURRENT PRICE", "PROFIT/LOSS");

        for (Map.Entry<String, Holding> e : holdings.entrySet()) {
            Stock s = market.getStock(e.getKey());
            Holding h = e.getValue();

            if (s != null) {
                double pl = (s.price - h.averageCost) * h.quantity;

                System.out.printf(Locale.US,
                        "%-12s %10d %14.2f %14.2f %+14.2f%n",
                        s.symbol, h.quantity, h.averageCost, s.price, pl);
            }
        }

        System.out.printf(Locale.US,
                "Total market value: Rs. %.2f%n", marketValue(market));
    }

    void displayPerformance(Market market) {
        double unrealized = unrealizedPL(market);

        System.out.println("\n--------- PORTFOLIO PERFORMANCE ---------");
        System.out.printf(Locale.US,
                "Current holdings value : Rs. %.2f%n",
                marketValue(market));

        System.out.printf(Locale.US,
                "Realized profit/loss   : Rs. %+.2f%n", realizedPL);

        System.out.printf(Locale.US,
                "Unrealized profit/loss : Rs. %+.2f%n", unrealized);

        System.out.printf(Locale.US,
                "Combined profit/loss   : Rs. %+.2f%n",
                realizedPL + unrealized);
    }

    void displayTransactions() {
        System.out.println("\n------------ TRANSACTION HISTORY ------------");

        if (transactions.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }

        for (Transaction t : transactions) {
            System.out.printf(Locale.US,
                    "%-5s %-12s Qty: %4d Price: Rs. %10.2f "
                            + "P/L: Rs. %+.2f  %s%n",
                    t.type, t.symbol, t.quantity, t.price,
                    t.profitLoss, t.dateTime);
        }
    }
}

class Transaction {
    String type;
    String symbol;
    int quantity;
    double price;
    double profitLoss;
    String dateTime;

    Transaction(String type, String symbol, int quantity,
                double price, double profitLoss) {
        this.type = type;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.profitLoss = profitLoss;

        this.dateTime = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    String toFileLine() {
        return type + "|" + symbol + "|" + quantity + "|"
                + price + "|" + dateTime + "|" + profitLoss;
    }

    static Transaction fromParts(String[] p, int start) {
        Transaction t = new Transaction(
                p[start],
                p[start + 1],
                Integer.parseInt(p[start + 2]),
                Double.parseDouble(p[start + 3]),
                Double.parseDouble(p[start + 5]));

        t.dateTime = p[start + 4];
        return t;
    }
}

class User {
    String name;
    double cash;
    Portfolio portfolio = new Portfolio();

    User(String name, double cash) {
        this.name = name;
        this.cash = cash;
    }

    void buy(Stock stock, int qty) {
        cash -= stock.price * qty;
        portfolio.buy(stock, qty);
    }

    boolean sell(Stock stock, int qty) {
        if (!portfolio.sell(stock, qty)) {
            return false;
        }

        cash += stock.price * qty;
        return true;
    }

    void displaySummary(Market market) {
        double value = portfolio.marketValue(market);

        System.out.println("\n------------ ACCOUNT SUMMARY ------------");
        System.out.println("User: " + name);

        System.out.printf(Locale.US,
                "Available cash       : Rs. %.2f%n", cash);

        System.out.printf(Locale.US,
                "Stock market value   : Rs. %.2f%n", value);

        System.out.printf(Locale.US,
                "Total account value  : Rs. %.2f%n", cash + value);

        System.out.printf(Locale.US,
                "Realized profit/loss : Rs. %+.2f%n",
                portfolio.realizedPL);

        System.out.printf(Locale.US,
                "Unrealized profit/loss: Rs. %+.2f%n",
                portfolio.unrealizedPL(market));
    }
}