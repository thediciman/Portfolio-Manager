package dici.portfolio.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Portfolio {

    final List<Position> openPositions;

    public Portfolio() {
        this.openPositions = new ArrayList<>();
    }

    public Set<String> getOwnedTickers() {
        return openPositions
            .stream()
            .map(Position::getTicker)
            .collect(Collectors.toSet());
    }

    public List<Position> getOpenPositions() {
        return openPositions;
    }

    public void addPosition(final Position position) {
        openPositions.add(position);
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("Open positions: ").append("\n");

        openPositions.forEach(position -> stringBuilder.append(position).append("\n"));

        return stringBuilder.toString();
    }

    public double getInitialValue() {
        return openPositions
            .stream()
            .mapToDouble(position -> position.getPrice() * position.getQuantity())
            .sum();
    }

    public double getCurrentValue(final Map<String, Double> tickersPrices) {
        return openPositions
            .stream()
            .mapToDouble(position -> tickersPrices.get(position.getTicker()) * position.getQuantity())
            .sum();
    }

}
