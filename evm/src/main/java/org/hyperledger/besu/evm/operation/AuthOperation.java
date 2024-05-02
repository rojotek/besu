package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;

import java.util.Optional;

/**
 * AUTH operation which sets the authorized address for the AUTHCALL operation.
 */
public class AuthOperation extends AbstractOperation {

  private final PrecompileContractRegistry precompileContractRegistry;

  public AuthOperation(final GasCalculator gasCalculator, final PrecompileContractRegistry precompileContractRegistry) {
    super(0xf6, "AUTH", 3, 0, gasCalculator);
    this.precompileContractRegistry = precompileContractRegistry;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    // Extract the signature from the input data
    // Assuming the signature is the last 65 bytes of the input data
    Bytes input = frame.getInputData();
    Bytes signature = input.slice(input.size() - 65, 65);

    // Perform ECDSA signature verification using a precompile
    PrecompiledContract authPrecompile = precompileContractRegistry.get(Address.fromHexString("0x01"));
    if (authPrecompile != null) {
      // Assuming the message hash is the input data without the last 65 bytes of the signature
      Bytes messageHash = input.slice(0, input.size() - 65);
      PrecompileContractResult precompileResult = authPrecompile.computePrecompile(messageHash, frame);
      if (precompileResult.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        Address authorizedAddress = Address.wrap(precompileResult.getOutput());
        // TODO: Set the authorized address in the appropriate context according to EIP-3074
        // frame.setAuthorizedAddress(authorizedAddress); // This method does not exist, need to find an alternative
        return new OperationResult(gasCalculator().getBaseTierGasCost(), null);
      } else {
        // Signature verification failed
        return new OperationResult(gasCalculator().getBaseTierGasCost(), null);
      }
    } else {
      // Precompile not defined
      return new OperationResult(gasCalculator().getBaseTierGasCost(), ExceptionalHaltReason.ILLEGAL_STATE_CHANGE); // Using ILLEGAL_STATE_CHANGE as a placeholder
    }
  }

  public long cost(final MessageFrame frame) {
    // Calculate the appropriate gas cost for the AUTH operation
    // The gas cost is defined in EIP-3074, for now, we'll return a fixed cost for illustration purposes
    // TODO: Update this to reflect the actual gas cost as defined in EIP-3074
    // Assuming the gas cost for AUTH is a fixed amount as per EIP-3074
    return 3000; // Placeholder for the actual gas cost defined in EIP-3074
  }
}
