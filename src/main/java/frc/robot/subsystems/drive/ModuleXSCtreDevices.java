// Copyright 2021-2024 FRC 6328
// http://github.com/Mechanical-Advantage
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// version 3 as published by the Free Software Foundation or
// available in the root directory of this project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

package frc.robot.subsystems.drive;

import com.ctre.phoenix.motorcontrol.InvertType;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.TalonSRXControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.can.TalonSRXConfiguration;
import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.MotorOutputConfigs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.VelocityTorqueCurrentFOC;
import com.ctre.phoenix6.controls.VoltageOut;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;

/**
 * Module IO implementation for Talon FX drive motor controller, Talon FX turn motor controller, and
 * CANcoder
 *
 *
 * <p>To calibrate the absolute encoder offsets, point the modules straight (such that forward
 * motion on the drive motor will propel the robot forward) and copy the reported values from the
 * absolute encoders using AdvantageScope. These values are logged under
 * "/Drive/ModuleX/TurnAbsolutePositionRad"
 */


 //This is untested but should serve as a starting place for brushless drive, brushed steer, modules.
 //wiring looks like a tun of fun. Drive motors and encoder are on canivore. steer motor on regular can.
public class ModuleXSCtreDevices implements ModuleIO {
  private final TalonFX driveTalon;
  private final TalonSRX turnTalon;
  private final CANcoder cancoder;

  private final StatusSignal<Double> drivePosition;
  private final StatusSignal<Double> driveVelocity;
  private final StatusSignal<Double> driveAppliedVolts;
  private final StatusSignal<Double> driveCurrent;

  private final StatusSignal<Double> turnAbsolutePosition;
  private final StatusSignal<Double> turnPosition;
  private final StatusSignal<Double> turnVelocity;

  // Gear ratios for WCP Swerve XS X2 14t
  private final double DRIVE_GEAR_RATIO = 4.71; 
  private final double TURN_GEAR_RATIO = 41.25; 

  private final boolean isTurnMotorInverted = true;
  private final Rotation2d absoluteEncoderOffset;

  private PIDController turnMotorPID = new PIDController(0.1, 0, 0);//untuned

  public ModuleXSCtreDevices(int index) {
    switch (index) {
      case 0: // fl
        driveTalon = new TalonFX(1, "Swerve");
        turnTalon = new TalonSRX(11);
        cancoder = new CANcoder(1, "Swerve");
        absoluteEncoderOffset = new Rotation2d(-1.358); // MUST BE CALIBRATED
        break;
      case 1: // fr
        driveTalon = new TalonFX(2, "Swerve");
        turnTalon = new TalonSRX(12);
        cancoder = new CANcoder(2, "Swerve");
        absoluteEncoderOffset = new Rotation2d(2.324+Math.PI); // MUST BE CALIBRATED
        break;
      case 2: // bl
        driveTalon = new TalonFX(3, "Swerve");
        turnTalon = new TalonSRX(13);
        cancoder = new CANcoder(3, "Swerve");
        absoluteEncoderOffset = new Rotation2d(0.851); // MUST BE CALIBRATED
        break;
      case 3: // br
        driveTalon = new TalonFX(4, "Swerve");
        turnTalon = new TalonSRX(14);
        cancoder = new CANcoder(4, "Swerve");
        absoluteEncoderOffset = new Rotation2d(1.578+Math.PI); // MUST BE CALIBRATED
        break;
      default:
        throw new RuntimeException("Invalid module index");
    }

    driveTalon.getConfigurator().apply(new TalonFXConfiguration());
    var driveConfig = new TalonFXConfiguration();
    driveConfig.CurrentLimits.StatorCurrentLimitEnable = true;
    driveConfig.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    driveConfig.Voltage.PeakForwardVoltage = 12.0;
    driveConfig.Voltage.PeakReverseVoltage = -12.0;
    driveConfig.Slot0.kV = 0.0; //0.12 means apply 12V for a Target Velocity of 100 RPS or 6000 RPM.
    driveConfig.Slot0.kS = 0.0;
    driveConfig.Slot0.kP = 2.0;
    driveConfig.Slot0.kI = 0.0;
    driveConfig.Slot0.kD = 0.0;
    driveConfig.TorqueCurrent.PeakForwardTorqueCurrent = 70;
    driveConfig.TorqueCurrent.PeakReverseTorqueCurrent = 70;
    driveConfig.ClosedLoopRamps.TorqueClosedLoopRampPeriod = 0.02;
    driveTalon.getConfigurator().apply(driveConfig);
    setDriveBrakeMode(true);

    var turnConfig = new TalonSRXConfiguration();
    turnConfig.peakCurrentLimit = 30;
    turnConfig.voltageCompSaturation = 12;
    setTurnBrakeMode(true);
    if (isTurnMotorInverted) {
      turnTalon.setInverted(InvertType.InvertMotorOutput);
    } else {
      turnTalon.setInverted(InvertType.None);
    }
    

    turnTalon.configAllSettings(turnConfig);

    var turnEncoder = new CANcoderConfiguration();
    turnEncoder.MagnetSensor.MagnetOffset = -absoluteEncoderOffset.getRotations();
    

    cancoder.getConfigurator().apply(turnEncoder);

    drivePosition = driveTalon.getPosition();
    driveVelocity = driveTalon.getVelocity();
    driveAppliedVolts = driveTalon.getMotorVoltage();
    driveCurrent = driveTalon.getStatorCurrent();

    turnAbsolutePosition = cancoder.getAbsolutePosition();
    turnPosition = cancoder.getAbsolutePosition();
    turnVelocity = cancoder.getVelocity();

    turnAbsolutePosition.setUpdateFrequency(350);
    BaseStatusSignal.setUpdateFrequencyForAll(
        230.0, drivePosition, turnPosition); // Required for odometry, use faster rate
    BaseStatusSignal.setUpdateFrequencyForAll(
        80.0,
        driveVelocity,
        driveAppliedVolts,
        driveCurrent,
        turnVelocity
        );
    driveTalon.optimizeBusUtilization();
    cancoder.optimizeBusUtilization();
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    BaseStatusSignal.refreshAll(
        drivePosition,
        driveVelocity,
        driveAppliedVolts,
        driveCurrent,
        turnAbsolutePosition,
        turnPosition,
        turnVelocity
        );

    inputs.drivePositionRad =
        Units.rotationsToRadians(drivePosition.getValueAsDouble()) / DRIVE_GEAR_RATIO;
    inputs.driveVelocityRadPerSec =
        Units.rotationsToRadians(driveVelocity.getValueAsDouble()) / DRIVE_GEAR_RATIO;
    inputs.driveAppliedVolts = driveAppliedVolts.getValueAsDouble();
    inputs.driveCurrentAmps = new double[] {driveCurrent.getValueAsDouble()};

    inputs.turnAbsolutePosition =
        Rotation2d.fromRotations(turnAbsolutePosition.getValueAsDouble())
            .minus(new Rotation2d(0));
    inputs.turnPosition =
        Rotation2d.fromRotations(turnPosition.getValueAsDouble() / TURN_GEAR_RATIO);
    inputs.turnVelocityRadPerSec =
        Units.rotationsToRadians(turnVelocity.getValueAsDouble()) / TURN_GEAR_RATIO;
    inputs.turnAppliedVolts = turnTalon.getMotorOutputVoltage();
    inputs.turnCurrentAmps = new double[] {turnTalon.getStatorCurrent()};

    inputs.isTalon = true;

    inputs.TalonError = turnMotorPID.getPositionError();
  }

  @Override
  public void setDriveVoltage(double volts) {
    driveTalon.setControl(new VoltageOut(volts));
  }

  @Override
  public void setDriveVelocity(double velocity) {
    driveTalon.setControl(new VelocityTorqueCurrentFOC(velocity).withSlot(0));
  }

  @Override
  public void setTurnPosition(double moduleAngle) {// dont question it. These are weird modules.
    double volts = turnMotorPID.calculate(cancoder.getAbsolutePosition().getValueAsDouble(), moduleAngle);
    turnTalon.set(TalonSRXControlMode.PercentOutput, volts/12);
  }

  @Override
  public void setTurnVoltage(double volts) {
    turnTalon.set(TalonSRXControlMode.PercentOutput, volts/12);
  }

  @Override
  public void setDriveBrakeMode(boolean enable) {
    var config = new MotorOutputConfigs();
    config.Inverted = InvertedValue.CounterClockwise_Positive;
    config.NeutralMode = enable ? NeutralModeValue.Brake : NeutralModeValue.Coast;
    driveTalon.getConfigurator().apply(config);
  }

  @Override
  public void setTurnBrakeMode(boolean enable) {
    if (!enable) {
      turnTalon.setNeutralMode(NeutralMode.Coast);
    } else {
      turnTalon.setNeutralMode(NeutralMode.Brake);
    }
  }
}
