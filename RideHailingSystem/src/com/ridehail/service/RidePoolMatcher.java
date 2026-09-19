package com.ridehail.service;

import com.ridehail.model.RideRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups pending ride requests into a single vehicle, maximizing total
 * passengers carried without exceeding the vehicle's remaining capacity.
 */
public class RidePoolMatcher {

    public List<RideRequest> findBestGroup(List<RideRequest> requests, int capacity) {
        return backtrack(requests, 0, capacity, new ArrayList<>());
    }

    private List<RideRequest> backtrack(List<RideRequest> requests, int index, int remainingCapacity,
                                     List<RideRequest> current) {
        if (index == requests.size()) {
            return new ArrayList<>(current);
        }

        RideRequest request = requests.get(index);

        List<RideRequest> withoutRequest = backtrack(requests, index + 1, remainingCapacity, current);

        List<RideRequest> withRequest = withoutRequest; // default if it doesn't fit
        if (request.getPassengerCount() <= remainingCapacity) {
            current.add(request);
            withRequest = backtrack(requests, index + 1, remainingCapacity - request.getPassengerCount(), current);
            current.remove(current.size() - 1);
        }

        return isBetter(withRequest, withoutRequest) ? withRequest : withoutRequest;
    }

    private boolean isBetter(List<RideRequest> a, List<RideRequest> b) {
        return totalPassengers(a) > totalPassengers(b);
    }

    public int totalPassengers(List<RideRequest> group) {
        int total = 0;
        for (RideRequest request : group) {
            total += request.getPassengerCount();
        }
        return total;
    }
}
