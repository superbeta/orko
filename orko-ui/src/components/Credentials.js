/*
 * Orko
 * Copyright © 2018-2019 Graham Crockford
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import React, { Component } from "react"
import OktaSignInWidget from "./OktaSignInWidget"
import Login from "./Login"
import { Dimmer } from "semantic-ui-react"

export default class Credentials extends Component {
  render() {
    if (this.props.config.clientId) {
      return (
        <Dimmer active>
          <OktaSignInWidget
            config={this.props.config}
            onSuccess={this.props.onSuccess}
            onError={this.props.onError}
          />
        </Dimmer>
      )
    } else {
      return (
        <Login
          error={this.props.error}
          onSuccess={this.props.onSuccess}
          onError={this.props.onError}
        />
      )
    }
  }
}
