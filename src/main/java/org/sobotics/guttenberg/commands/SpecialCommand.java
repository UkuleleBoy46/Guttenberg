/*
 * Copyright (C) 2019 SOBotics
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.sobotics.guttenberg.commands;

import org.sobotics.chatexchange.chat.Room;
import org.sobotics.guttenberg.services.RunnerService;

/**
 * Created by bhargav.h on 30-Sep-16.
 */
public interface SpecialCommand {
  public boolean validate();

  public void execute(Room room, RunnerService instance);

  public String description();

  public String name();

  public boolean availableInStandby();
}
