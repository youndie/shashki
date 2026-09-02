package io.github.youndie.shashki.server.feature.receipt.domain

import io.github.youndie.shashki.server.common.UseCase
import io.github.youndie.shashki.server.common.suspendRunCatching

/**
 * One receipt, for one finished ride.
 *
 * **It returns whether the mail went, and the caller decides what that means.** A settlement that
 * rolled back because a mail server was down would be the tail wagging the dog; a settlement that
 * silently swallowed the failure would leave a rider charged with nothing to show for it. Neither
 * decision belongs to the sender, so this reports and stops.
 */
public class SendReceiptUseCase(
    private val sender: ReceiptSender,
) : UseCase<Receipt, Boolean> {
    override suspend fun invoke(params: Receipt): Result<Boolean> = suspendRunCatching { sender.send(params) }
}
