package dici.portfolio.entities;

import org.joda.time.DateTime;

public class Position {

    private final String ticker;
    private final double quantity;
    private final double price;
    private final DateTime purchaseDate;

    public Position(final String ticker, final double quantity, final double price, final DateTime purchaseDate) {
        this.ticker = ticker;
        this.quantity = quantity;
        this.price = price;
        this.purchaseDate = purchaseDate;
    }

    public String getTicker() {
        return ticker;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public DateTime getPurchaseDate() {
        return purchaseDate;
    }

    @Override
    public String toString() {
        return "Position{" +
            "ticker='" + ticker + '\'' +
            ", quantity=" + quantity +
            ", price=" + price +
            ", purchaseDate=" + purchaseDate +
            '}';
    }

}