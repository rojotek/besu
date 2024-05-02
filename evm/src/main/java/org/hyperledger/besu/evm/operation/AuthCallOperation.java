package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

import java.util.Optional;

public class AuthCallOperation extends AbstractOperation {

  private final PrecompileContractRegistry precompileContractRegistry;
  private final MetricsSystem metricsSystem;

  public AuthCallOperation(final GasCalculator gasCalculator, final PrecompileContractRegistry precompileContractRegistry, final MetricsSystem metricsSystem) {
    super(0xf7, "AUTHCALL", 7, 1, false, 1, gasCalculator);
    this.precompileContractRegistry = precompileContractRegistry;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final OperationTimer timer = metricsSystem.createTimer("EVM", "AUTHCALL");
    try (final OperationTimer.TimingContext ignored = timer.start()) {
      // Extract the gas, address, value, argsOffset, argsLength, retOffset, retLength from the stack
      final long gas = frame.popStackItem().asUInt256().toLong();
      final Address address = Words.toAddress(frame.popStackItem());
      final Wei value = Wei.wrap(frame.popStackItem());
      final long argsOffset = frame.popStackItem().asUInt256().toLong();
      final long argsLength = frame.popStackItem().asUInt256().toLong();
      final long retOffset = frame.popStackItem().asUInt256().toLong();
      final long retLength = frame.popStackItem().asUInt256().toLong();

      // Verify that the address is the authorized address set by the AUTH operation
      if (!address.equals(frame.getAuthorizedAddress())) {
        return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
      }

      // Perform the call operation
      MessageFrame childFrame = MessageFrame.builder()
        .type(MessageFrame.Type.MESSAGE_CALL)
        .messageFrameStack(frame.getMessageFrameStack())
        .blockchain(frame.getBlockchain())
        .worldState(frame.getWorldState())
        .initialGas(Gas.of(gas))
        .address(address)
        .originator(frame.getOriginatorAddress())
        .contract(frame.getContractAddress())
        .gasPrice(frame.getGasPrice())
        .inputData(frame.readMemory(argsOffset, argsLength))
        .sender(frame.getRecipientAddress())
        .value(value)
        .apparentValue(value)
        .code(frame.getWorldState().getCode(address))
        .blockHeader(frame.getBlockHeader())
        .depth(frame.getDepth() + 1)
        .completer(child -> {})
        .miningBeneficiary(frame.getMiningBeneficiary())
        .blockHashLookup(frame.getBlockHashLookup())
        .maxStackSize(frame.getMaxStackSize())
        .build();

      evm.process(childFrame, OperationTracer.NO_TRACING);

      // Handle the result of the call operation
      if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        frame.writeMemory(retOffset, childFrame.getOutputData());
        return new OperationResult(Optional.empty(), Optional.empty());
      } else {
        return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
      }
    } catch (Exception e) {
      // If there is an exception during the AUTHCALL operation, we halt exceptionally
      return new OperationResult(Optional.of(gasCalculator().getBaseTierGasCost()), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
    }
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    // TODO: Calculate the appropriate gas cost for the AUTHCALL operation
    // The gas cost is defined in EIP-3074, for now, we'll return a fixed cost for illustration purposes

    // Placeholder for the AUTHCALL operation gas cost
    // The actual gas cost calculation will be implemented according to EIP-3074 specifications
    return Gas.of(3400); // Placeholder for the actual gas cost defined in EIP-3074
  }
}
