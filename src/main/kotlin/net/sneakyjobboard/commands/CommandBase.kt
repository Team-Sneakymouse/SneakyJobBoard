package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.command.Command

/**
 * Extension of the bukkit Command class, which sets all the properties as they are shared between our commands.
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${SneakyJobBoard.IDENTIFIER}.command.$name"
    }

}
