package frc.robot.subsystems.Vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.PhotonPoseEstimator.PoseStrategy;
import org.photonvision.targeting.PhotonPipelineResult;
import org.photonvision.targeting.PhotonTrackedTarget;
import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.networktables.NetworkTableInstance;

//Made by 281 I dont know tweak values in get filtered result till it behave right
public class SoloCameraContainer implements CameraContainer {
  private final PhotonCamera camera;
  private final PhotonPoseEstimator estimator;

  public SoloCameraContainer(String cameraName, Transform3d robotToCamera,
      AprilTagFieldLayout fieldLayout) {
    camera = new PhotonCamera(cameraName);
    estimator = new PhotonPoseEstimator(fieldLayout, PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
        camera, robotToCamera);
    estimator.setMultiTagFallbackStrategy(PoseStrategy.LOWEST_AMBIGUITY);
  }

  public SoloCameraContainer(String cameraName, Transform3d robotToCamera,
      AprilTagFieldLayout fieldLayout, NetworkTableInstance ni) {
    camera = new PhotonCamera(ni, cameraName);
    estimator = new PhotonPoseEstimator(fieldLayout, PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
        camera, robotToCamera);
    estimator.setMultiTagFallbackStrategy(PoseStrategy.LOWEST_AMBIGUITY);
  }

  @Override
  public PhotonPipelineResult getFilteredResult() {
    PhotonPipelineResult result = camera.getLatestResult();

    List<PhotonTrackedTarget> filteredTargets = new ArrayList<>();

    for (PhotonTrackedTarget target : result.getTargets()) {
      if (target.getPoseAmbiguity() > 0.2)//max allowed ambiguity
        continue;
      if (Math
        .abs(target.getBestCameraToTarget().getX()) > 5.5) //max tag distance.
      continue;


      filteredTargets.add(target);
    }

    PhotonPipelineResult fliteredResult =
        new PhotonPipelineResult(result.getLatencyMillis(), filteredTargets);
    fliteredResult.setTimestampSeconds(result.getTimestampSeconds());

    return fliteredResult;
  }

  @Override
  public Optional<Pose2d> getEstimatedPose() {
    Optional<EstimatedRobotPose> estimatedPose = estimator.update(getFilteredResult());
    if (estimatedPose.isPresent()) {
      return Optional.of(estimatedPose.get().estimatedPose.toPose2d());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public double getLatency() {
    return getFilteredResult().getLatencyMillis();
  }

  @Override
  public boolean hasTargets() {
    return getFilteredResult().hasTargets();
  }

  @Override
  public int getTargetCount() {
    return getFilteredResult().getTargets().size();
  }

  @Override
  public List<EntechTargetData> getTargetData() {
    List<PhotonTrackedTarget> targets = getFilteredResult().getTargets();
    List<Integer> targetIds = new ArrayList<>();
    for (PhotonTrackedTarget target : targets) {
      targetIds.add(target.getFiducialId());
    }

    List<EntechTargetData> data = new ArrayList<>();
    data.add(new EntechTargetData(targetIds, camera.getName()));
    return data;
  }

  public boolean isCameraConnected() {
    return camera.isConnected();
  }
}