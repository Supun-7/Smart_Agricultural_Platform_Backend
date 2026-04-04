package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandRegistrationRequest;
import CHC.Team.Ceylon.Harvest.Capital.dto.farmer.LandResponse;

import java.util.List;
import java.util.Map;

public interface FarmerDashboardService {
    Map<String, Object> getFarmerDashboard(Long userId);

    LandResponse createLand(Long userId, LandRegistrationRequest request);

    List<LandResponse> getFarmerLands(Long userId);

    LandResponse updateLandStatus(Long userId, Long landId, boolean isActive);
}
