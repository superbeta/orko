import React from "react"
import { connect } from "react-redux"

import { Icon } from "semantic-ui-react"

import * as coinsActions from "../store/coins/actions"
import * as uiActions from "../store/ui/actions"
import { getCoinsForDisplay } from "../selectors/coins"

import Section from "../components/primitives/Section"
import Link from "../components/primitives/Link"
import Coins from "../components/Coins"
import GetPageVisibility from "../components/GetPageVisibility"
import RenderIf from "../components/RenderIf"

const buttons = () => (
  <Link to="/addCoin" color="heading">
    <Icon name="add" />
  </Link>
)

const CoinsCointainer = ({ data, dispatch }) => (
  <GetPageVisibility>
    {visible => (
      <RenderIf condition={visible}>
        <Section
          id="coinList"
          heading="Coins"
          nopadding
          buttons={buttons}
        >
          <Coins
            data={data}
            onRemove={coin => dispatch(coinsActions.remove(coin))}
            onClickAlerts={coin => dispatch(uiActions.openAlerts(coin))}
            visible
          />
        </Section>
      </RenderIf>
    )}
  </GetPageVisibility>
)

function mapStateToProps(state) {
  return {
    data: getCoinsForDisplay(state)
  }
}

export default connect(mapStateToProps)(CoinsCointainer)