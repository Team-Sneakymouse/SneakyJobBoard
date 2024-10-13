package net.sneakyjobboard.commands

import net.sneakyjobboard.SneakyJobBoard
import org.bukkit.command.Command

abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${SneakyJobBoard.IDENTIFIER}.command.$name"
    }

}
