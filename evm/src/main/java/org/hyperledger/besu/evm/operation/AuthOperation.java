package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.hyperledger.besu.datatypes.Bytes;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompileContract;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;

import java.util.Optional;

/**
 * AUTH operation which sets the authorized address for the AUTHCALL operation.
 */
public class AuthOperation extends AbstractOperation {

  private final PrecompileContractRegistry precompileContractRegistry;
  private final MetricsSystem metricsSystem;

  public AuthOperation(final GasCalculator gasCalculator, final PrecompileContractRegistry precompileContractRegistry, final MetricsSystem metricsSystem) {
    super(0xf6, "AUTH", 3, 0, false, 1, gasCalculator);
    this.precompileContractRegistry = precompileContractRegistry;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final OperationTimer timer = metricsSystem.createTimer("EVM", "AUTH");
    try (final OperationTimer.TimingContext ignored = timer.start()) {
      // Extract the signature from the input data
      // Assuming the signature is the last 65 bytes of the input data
      Bytes input = frame.getInputData();
      Bytes signature = input.slice(input.size() - 65, 65);

      // Perform ECDSA signature verification using a precompile
      Optional<PrecompileContract> authPrecompileOptional = precompileContractRegistry.get(PrecompiledContractIdentifier.ECDSA_RECOVER);
      if (authPrecompileOptional.isPresent()) {
        PrecompileContract authPrecompile = authPrecompileOptional.get();
        // Assuming the message hash is the input data without the last 65 bytes of the signature
        Bytes messageHash = input.slice(0, input.size() - 65);
        Bytes output = authPrecompile.compute(messageHash, signature, evm);
        if (output != null) {
          Address authorizedAddress = Address.wrap(output);
          // Set the authorized address in the MessageFrame
          frame.setAuthorizedAddress(authorizedAddress);
          return new OperationResult(Optional.empty(), Optional.empty());
        } else {
          // Signature verification failed
          return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        }
      } else {
        return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.PRECOMPILE_NOT_DEFINED));
      }
    } catch (Exception e) {
      // If there is an exception during the AUTH operation, we halt exceptionally
      return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
    }
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    // Calculate the appropriate gas cost for the AUTH operation
    // The gas cost is defined in EIP-3074, for now, we'll return a fixed cost for illustration purposes
    // TODO: Update this to reflect the actual gas cost as defined in EIP-3074
    // Assuming the gas cost for AUTH is a fixed amount as per EIP-3074
    return Gas.of(3000); // Placeholder for the actual gas cost defined in EIP-3074
  }
}
